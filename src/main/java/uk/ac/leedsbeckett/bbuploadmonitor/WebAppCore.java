/*
 * Copyright 2022 Leeds Beckett University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.leedsbeckett.bbuploadmonitor;

import blackboard.data.registry.SystemRegistryUtil;
import blackboard.data.user.User;
import blackboard.persist.Id;
import blackboard.persist.user.UserDbLoader;
import blackboard.platform.intl.BbLocale;
import blackboard.platform.plugin.PlugInUtil;
import com.xythos.common.api.NetworkAddress;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.events.EventSubQueue;
import com.xythos.fileSystem.events.FileSystemEntryCreatedEventImpl;
import com.xythos.fileSystem.events.FileSystemEntryMovedEventImpl;
import com.xythos.fileSystem.events.StorageServerEventBrokerImpl;
import com.xythos.fileSystem.events.StorageServerEventListener;
import com.xythos.security.api.Context;
import com.xythos.security.api.ContextFactory;
import com.xythos.security.api.PrincipalManager;
import com.xythos.security.api.UserBase;
import com.xythos.storageServer.api.CreateDirectoryData;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemDirectory;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.FileSystemEntryCreatedEvent;
import com.xythos.storageServer.api.FileSystemEntryMovedEvent;
import com.xythos.storageServer.api.FileSystemEvent;
import com.xythos.storageServer.api.VetoEventException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import javax.servlet.annotation.WebListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import uk.ac.leedsbeckett.bbb2utils.peertopeer.BuildingBlockCoordinator;
import uk.ac.leedsbeckett.bbb2utils.peertopeer.BuildingBlockPeerMessageListener;


/**
 * This is the central object in this web application. It is instantiated once 
 * when the application starts because it is annotated as a WebListener. After
 * the servlet context is created the contextInitialized method of this class
 * is called. This object puts a reference to itself in an attribute of the
 * servlet context so that servlets can find it and interact with it.
 * 
 * @author jon
 */
@WebListener
public class WebAppCore implements ServletContextListener, StorageServerEventListener, BuildingBlockPeerMessageListener
{
  public final static String ATTRIBUTE_CONTEXTBBMONITOR = WebAppCore.class.getCanonicalName();
  private static final StringBuilder bootstraplog = new StringBuilder();
  
  public static SimpleDateFormat dateformatforfilenames = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
  public static SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
  /**
   * logger is for technical/diagnostic information.
   */
  public Logger logger = null;
  
  /**
   * datalogger is where the creation of big files by users is logged.
   */
  public Logger datalogger = null;
  
  RollingFileAppender datarfapp                          = null;
  private final Properties defaultproperties             = new Properties();
  private final BuildingBlockProperties configproperties = new BuildingBlockProperties(defaultproperties);
  private final BbLocale locale = new BbLocale();
  String contextpath;
  String buildingblockhandle;
  String buildingblockvid;
  String pluginid;
  boolean monitoringxythos=false;
  String serverid;  
  int filesize=100;  // in mega bytes
  String action = "none";
  String emailsubject, emailbody, specialemailbody, filematchingex, overwritefile;
  InternetAddress emailfrom;
  File propsfile;

  private final Class[] listensfor = {FileSystemEntryCreatedEventImpl.class,FileSystemEntryMovedEventImpl.class};
  
  VirtualServer xythosvserver;
  String xythosprincipalid;
  UserBase xythosadminuser;  
  
  
  public Path virtualserverbase=null;
  public Path pluginbase=null;
  public Path logbase=null;
  public Path configbase=null;
  
  BuildingBlockCoordinator bbcoord;
  FileProcessWorker fileprocessworker = new FileProcessWorker( this );
  
  /**
   * The constructor just checks to see how many times it has been called.
   * This constructor is called by the servlet container.
   */
  public WebAppCore()
  {
    WebAppCore.logToBuffer("ContextListener constructor." );    
  }

  public Path getVirtualserverbase()
  {
    return virtualserverbase;
  }


  
  
  
  /**
   * This method gets called by the servlet container after the servlet 
   * context has been set up. So, this is where BBMonitor initialises itself.
   * 
   * @param sce This servlet context event includes a reference to the servlet context.
   */
  @Override
  public void contextInitialized(ServletContextEvent sce)
  {
    try
    {
      init( sce );
    }
    catch ( Exception e )
    {
      WebAppCore.logToBuffer( "BB init failed " + e.toString() );
      StringWriter writer = new StringWriter();
      e.printStackTrace( new PrintWriter( writer ) );
      WebAppCore.logToBuffer( writer.getBuffer().toString() );
    }    
  }
  
  
  public void init( ServletContextEvent sce ) throws JMSException
  {
    WebAppCore.logToBuffer("BB plugin init");
    sce.getServletContext().setAttribute( ATTRIBUTE_CONTEXTBBMONITOR, this );
    try
    {
      serverid = InetAddress.getLocalHost().getHostName();
      WebAppCore.logToBuffer( serverid );
    }
    catch (UnknownHostException ex)
    {
      WebAppCore.logToBuffer( "Unable to find local IP address." );
      WebAppCore.logToBuffer( ex );
    }
    
    if ( !initDefaultSettings( sce ) )
      return;  
    if ( !loadSettings() )
      return;
        
    if ( !initXythos() )
      return;
    startMonitoringXythos();
    contextpath = sce.getServletContext().getContextPath();
    
    bbcoord = new BuildingBlockCoordinator( buildingblockvid, buildingblockhandle, serverid, this, logger );
    bbcoord.setPingRate( 0 );
    // This is an asynchronous start.  It creates a thread to start the messaging
    // but returns right away. This is because there could be issues connecting to
    // the message broker over the network that could make starting the building block
    // hang for ages.
    bbcoord.start();

    fileprocessworker.start();
  }


  /**
   * Default settings are built into the web application. Here they are loaded
   * and also two key entries which are used to locate folders in the BB system.
   * @param sce
   * @return 
   */
  public boolean initDefaultSettings(ServletContextEvent sce)
  {
    String strfile = sce.getServletContext().getRealPath("WEB-INF/defaultsettings.properties" );
    WebAppCore.logToBuffer("Expecting to find default properties here: " + strfile );
    File file = new File( strfile );
    if ( !file.exists() )
    {
      WebAppCore.logToBuffer("It doesn't exist - cannot start." );
      return false;
    }
    
    try ( FileReader reader = new FileReader( file ) )
    {
      defaultproperties.load(reader);
    }
    catch (Exception ex)
    {
      logToBuffer( ex );
      return false;
    }
    
    buildingblockhandle = defaultproperties.getProperty("buildingblockhandle","");
    buildingblockvid = defaultproperties.getProperty("buildingblockvendorid","");
    if ( buildingblockhandle.length() == 0 || buildingblockvid.length() == 0 )
    {
      WebAppCore.logToBuffer( "Cannot work out bb handle or vendor id so can't load configuration." );
      return false;      
    }
    pluginid = buildingblockvid + "_" + buildingblockhandle;
    
    try
    {
      configbase = Paths.get( PlugInUtil.getConfigDirectory( buildingblockvid, buildingblockhandle ).getPath() );
      logbase    = configbase.resolve( "log" );
      pluginbase = configbase.getParent();      
      Path p = pluginbase; 
      while ( p.getNameCount() > 2 )
      {
        if ( "vi".equals( p.getParent().getFileName().toString() ) )
          break;
        p = p.getParent();
      }
      virtualserverbase = p;

      WebAppCore.logToBuffer( "virtualserverbase = " + virtualserverbase.toString() );
      WebAppCore.logToBuffer( "pluginbase        = " + pluginbase.toString() );
      WebAppCore.logToBuffer( "configbase        = " + configbase.toString() );
      WebAppCore.logToBuffer( "logbase           = " + logbase.toString()    );
    }
    catch ( Exception e )
    {
      WebAppCore.logToBuffer( e );      
    }
    
    return true;
  }
  
  /**
   * Load the variable properties file and start logging.
   * @return Success
   */
  public boolean loadSettings()
  {
    try
    {      
      initLogging();
      
      propsfile = configbase.resolve( buildingblockhandle + ".properties" ).toFile();
      logger.info("Config properties file is here: " + propsfile );
      if ( !propsfile.exists() )
      {
        logger.info( "Doesn't exist so creating it now." );
        propsfile.createNewFile();
      }
      
      return reloadSettings();
    }
    catch ( Throwable th )
    {
      WebAppCore.logToBuffer( "Failed to load configuration." );
      logToBuffer( th );
      return false;            
    }
  }
  
  /**
   * Manually configure logging so that the log files for this application
   * go where we want them and not into general log files for BB.
   * @param logfilefolder 
   */
  public void initLogging(  ) throws IOException
  {
    if ( !Files.exists( logbase ) )
      Files.createDirectory( logbase );
    
    Logger rootlog = LogManager.getLoggerRepository().getRootLogger();
    if ( rootlog == null )
      WebAppCore.logToBuffer( "No root log found." );
    else
      WebAppCore.logToBuffer( "Root log: " + rootlog.getName() );
    
    logger = LogManager.getLoggerRepository().getLogger(WebAppCore.class.getName() );
    logger.setLevel( Level.INFO );
    String logfilename = logbase.resolve( serverid + ".log" ).toString();
    WebAppCore.logToBuffer( logfilename );
    RollingFileAppender rfapp = 
        new RollingFileAppender( 
            new PatternLayout( "%d{ISO8601} %-5p: %m%n" ), 
            logfilename, 
            true );
    rfapp.setMaxBackupIndex( 100 );
    rfapp.setMaxFileSize( "2MB" );
    logger.removeAllAppenders();
    logger.addAppender( rfapp );
    logger.info( "==========================================================" );
    logger.info( "Log file has been opened." );
    logger.info( "==========================================================" );
    
    
    datalogger = LogManager.getLoggerRepository().getLogger(WebAppCore.class.getName() + "/datalogger" );
    datalogger.setLevel( Level.INFO );
    datalogger.removeAllAppenders();
  }
  
  
  public boolean initXythos()
  {
    Context context = null;
    xythosvserver = VirtualServer.getDefaultVirtualServer();
    logger.info( "Default xythos virtual server " + xythosvserver.getName() );
    try
    {
      for ( String location : PrincipalManager.getUserLocations() )
      {
        logger.info( "User Location: " + location );
        xythosadminuser = PrincipalManager.findUser( configproperties.getProperty("username"), location );
        if ( xythosadminuser == null )
          logger.info( "Did not find user here." );
        else
        {
          logger.info( "User: " + xythosadminuser.getID() + " " + xythosadminuser.getPrincipalID() + " " + xythosadminuser.getDisplayName() + " " + xythosadminuser.getLocation() );
          break;
        }
      }

      if ( xythosadminuser == null )
      {
        logger.error( "Unable to find user " + configproperties.getProperty("username") );
        return false;
      }
        
      FileSystemEntry pluginsdir, plugindir;
      context = ContextFactory.create( xythosadminuser, new Properties() );
      pluginsdir = FileSystem.findEntry( xythosvserver, "/internal/plugins", false, context );
      if ( pluginsdir == null )
      {
        logger.error( "Can't find plugins directory in xythos." );
        return false;
      }
      
      xythosprincipalid = xythosadminuser.getPrincipalID();      
      plugindir = FileSystem.findEntry( xythosvserver, "/internal/plugins/" + pluginid, false, context );
      if ( plugindir == null )
      {
        logger.info( "Creating subfolder " + pluginid );
        CreateDirectoryData cdd = new CreateDirectoryData( xythosvserver, "/internal/plugins/", pluginid, xythosprincipalid );
        plugindir = FileSystem.createDirectory( cdd, context );
      }
      if ( !(plugindir instanceof FileSystemDirectory ) )
      {
        logger.error( "Expecting directory named /internal/plugins/" +  pluginid + ". But it is not a directory." );
        return false;
      }
      
    }
    catch (Exception ex)
    {
      logger.error( "Exception trying to initialise Xythos content collection files." );
      logger.error( ex );
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch (Exception ex1) { logger.error( "Unable to roll back Xythos context after exception", ex ); context = null; }
      }
      return false;
    }
    finally
    {
      // commit regardless of whether it was necessary to create the file.
      // this is to ensure resources are released.
      if ( context != null )
        try { context.commitContext();}
        catch (XythosException ex) { logger.error( "Exception attempting to commit xythos context. ", ex );}      
    }

    return true;
  }
  
  
  /**
   * This is called when the servlet context is being shut down.
   * @param sce 
   */
  @Override
  public void contextDestroyed(ServletContextEvent sce)
  {
    logger.info("LBU BB upload monitor plugin destroy");    

    try { this.fileprocessworker.worker.interrupt(); }
    catch ( Throwable th ) { logger.error( "Exception trying to stop file processing worker thread", th ); }
    
    try { stopMonitoringXythos(); }
    catch ( Throwable th ) { logger.error( "Exception trying to stop Xythos monitoring", th ); }
    
    try { bbcoord.destroy(); }
    catch ( JMSException ex ) { logger.error( "Problem destroying bb coordinator", ex ); }    
  }

  /**
   * This is called by the server coordinator if one of the servers
   * indicates that settings have changed.So, this server must load the
   * settings and reconfigure.
   * @return 
   */
  public boolean reloadSettings()
  {
    logger.info( "reloadSettings()" );
    try ( FileReader reader = new FileReader( propsfile ) )
    {
      configproperties.load(reader);
      filesize = configproperties.getFileSize();
      logger.setLevel( configproperties.getLogLevel() );
      action = configproperties.getAction();
      emailsubject = configproperties.getEMailSubject();
      emailbody = configproperties.getEMailBody();
      filematchingex = configproperties.getFileMatchingExpression();
      overwritefile = configproperties.getOverwriteFilePath();
      specialemailbody = configproperties.getSpecialEMailBody();
      emailfrom = new InternetAddress( configproperties.getEMailFrom() );
      emailfrom.setPersonal( configproperties.getEMailFromName() );
      return true;
    }
    catch (Exception ex)
    {
      logger.error( "Unable to load properties from file.", ex );
      return false;
    }    
  }

  public BuildingBlockProperties getProperties()
  {
    return configproperties;
  }


  public void saveProperties()
  {
    try ( FileWriter writer = new FileWriter( propsfile ) )
    {
      configproperties.store(writer, serverid);
      bbcoord.sendTextMessageToAll( "reconfigure" );
      logger.info( "Saved settings and told all servers." );
    }
    catch (Exception ex)
    {
      logger.error( "Unable to save properties to file." );
      logger.error( ex );
    }
  }

  
  
  /**  
   * This is called when this server becomes responsible for monitoring the
   * Xythos content collection. It must connect to the data log file and
   * register with Xythos.
   */
  protected void startMonitoringXythos()
  {
    if ( monitoringxythos )
      return;
    try
    {
      logger.info("Starting listening to Xythos." );
    
      String logfilename = logbase.resolve( "bigfiles_" + serverid + ".log" ).toString();
      logger.info(logfilename );
      datarfapp = 
          new RollingFileAppender( 
              new PatternLayout( "%d{ISO8601},%m%n" ), 
              logfilename, 
              true );
      datarfapp.setMaxBackupIndex( 20 );
      datarfapp.setMaxFileSize( "100MB" );
      datalogger.removeAllAppenders();
      datalogger.addAppender( datarfapp );

      StorageServerEventBrokerImpl.addAsyncListener(this);
      //StorageServerEventBrokerImpl.addSyncListener(this);
      //StorageServerEventBrokerImpl.addImmedListener(this);
      monitoringxythos = true;
    }
    catch ( Throwable th )
    {
      logger.error( th );
    }   
  }

  /**
   * Closes the data log file so another server can open it. This de-registers
   * with Xythos.
   */
  public void stopMonitoringXythos()
  {
    if ( !monitoringxythos )
      return;
    datalogger.removeAllAppenders();
    if ( datarfapp != null )
    {
      datarfapp.close();
      datarfapp = null;
    }
    logger.info( "Stopping listening to Xythos." );
    StorageServerEventBrokerImpl.removeAsyncListener( this );    
    monitoringxythos = false;
  }
  
  
  /**
   * Part of the implementation of StorageServerEventListener interface.
   * @return List of event classes we are interested in.
   */
  @Override
  public Class[] listensFor()
  {
    return listensfor;
  }

  /**
   * Part of the implementation of StorageServerEventListener interface.Receives notification of events.
   * Some events are selected for logging.
   * Before logging the user ID string from Xythos is converted into a BB
   * User object so information about the user who created the file can be
   * logged.
   * 
   * @throws com.xythos.storageServer.api.VetoEventException
   * Throwing this exception vetoes the event being passed on to other listeners.
   * It does not veto the action that caused the event.
   */
  @Override
  public void processEvent(Context cntxt, FileSystemEvent fse) throws Exception, VetoEventException
  {  
    try
    {
      FileSystemEntry entry;
      logger.debug( "BlackboardBackend -              event = " + fse.getClass() );
      if ( fse instanceof FileSystemEntryCreatedEvent )
      {
        FileSystemEntryCreatedEvent fsece = (FileSystemEntryCreatedEvent)fse;
        logger.debug( "BlackboardBackend - create entry event = " + fsece.getFileSystemEntryName() );
        logger.debug( "BlackboardBackend -           entry id = " + fsece.getEntryID()             );
        logger.debug( "BlackboardBackend -               size = " + fsece.getSize()                );
        entry = FileSystem.findEntryFromEntryID( fsece.getEntryID(), false, cntxt );
      }
      else if ( fse instanceof FileSystemEntryMovedEvent )
      {
        FileSystemEntryMovedEvent fseme = (FileSystemEntryMovedEvent)fse;
        logger.debug( "BlackboardBackend -   move entry event = " + fseme.getFileSystemEntryName() );
        logger.debug( "BlackboardBackend -                 to = " + fseme.getToName()              );
        logger.debug( "BlackboardBackend -           entry id = " + fseme.getEntryID()             );
        entry = FileSystem.findEntryFromEntryID( fseme.getEntryID(), false, cntxt );
      }
      else
      {
        // not an interesting class of event
        entry = null;
        return;
      }
      
      if ( entry == null )
      {
        logger.debug( "File system entry with that id not found." );
        return;
      }


      long size = entry.getEntrySize();
      if ( size < (1024*1024*filesize) )
        return;
      String filepath = entry.getName();          //fsece.getFileSystemEntryName();
      String longid = entry.getCreatedByPrincipalID();

      logger.info( "File over " + filesize + " Mbytes created: " + filepath + 
                   " Size = " + (size/(1024*1024)) + "Mb  Owner = " + longid );
      
      if ( longid.startsWith( "BB:U:" ) )
      {
        String shortid = longid.substring( 5 );
        UserDbLoader userdbloader = UserDbLoader.Default.getInstance();
        User user = userdbloader.loadById( Id.toId( User.DATA_TYPE, shortid ) );
        String name = user.formatName( locale, BbLocale.Name.DEFAULT );
        String type = entry.getFileContentType();
        String un = user.getUserName();

        Properties properties = new Properties();
        properties.setProperty( "filename", filepath );
        properties.setProperty( "filesize_mb", Long.toString( Math.round( (double)size / (1024.0*1024.0) ) ) );
        properties.setProperty( "filetype", entry.getFileContentType() );
        properties.setProperty( "name", name );
        properties.setProperty( "user_name", un );
        properties.setProperty( "user_email", user.getEmailAddress() );

        logger.info( "Created by " + longid + "  =  " + shortid );
        logger.info( "User name of file creator: " + user.getUserName() );
        logger.info( "Email of file creator: "     + user.getEmailAddress() );
        logger.info( "Name of file creator: "      + name );
        datalogger.info( 
                filepath + "," +
                (size/(1024*1024))             + "," + 
                user.getUserName()             + "," +
                user.getEmailAddress()         + "," +
                name                           + "," +
                type                                      );
        logger.info( "Current action is " + action );
        if ( "mode1".equals( action ) || ( "mode1a".equals( action ) && un.endsWith( "admin" ) ) )
        {
          if ( filepath.startsWith( "/courses/" ) && 
               type.startsWith( "video/" )        && 
               size > 100000000 )
          {
            logger.info( "Taking mode1 or mode1a action." );
            boolean isspecial = filepath.matches( filematchingex );
            logger.info( "Does " + filepath + " match " + filematchingex + "? " + isspecial );
            String m = isspecial?specialemailbody:emailbody;
            if ( isspecial )
              fileprocessworker.add( filepath, entry.getVirtualServer() );
            InternetAddress recipient = new InternetAddress( user.getEmailAddress() );
            recipient.setPersonal( name );
            sendEmail( recipient, properties, m );
          }
        }
      }
    }
    catch ( Exception e )
    {
      logger.error( "Exception while handling file created event.", e );
    }
  }

  /**
   * Part of the implementation of StorageServerEventListener interface.
   * @return In our case there is no sub queue - returns null.
   */
  @Override
  public EventSubQueue getEventSubQueue()
  {
    return null;
  }
   

  public void sendEmail( InternetAddress mainrecipient, Properties properties, String formattedbody )
  {
    for ( String p : properties.stringPropertyNames() )
      formattedbody = formattedbody.replace( "{"+p+"}", properties.getProperty( p ) );
    InternetAddress[] recipients = { mainrecipient };
    InternetAddress[] cclist     = { emailfrom     };
    logger.info( "Sending email to " + mainrecipient );
    logger.info( "from "    + emailfrom );
    logger.info( "subject " + emailsubject );
    logger.info( "body "    + formattedbody );
    try    
    {
      sendHtmlEmail( emailsubject, emailfrom, null, recipients, cclist, formattedbody );
    }
    catch (MessagingException ex)
    {
      logger.error( "Exception while attempting to send an email.", ex );
    }
  }

  
  /**
   * For servlet to find out where logs are located.
   * @return Full path of this app's log folder.
   */
  public String getLogFolder()
  {
    return this.logbase.toString();
  }
  
  
  /**
   * For servlet - returns the logging text that was recorded before the
   * proper logs on file were initialised.
   * @return 
   */
  public static String getBootstrapLog()
  {
    return bootstraplog.toString();
  }


  /**
   * Logs to a string buffer while this object is initializing
   * @param s 
   */
  private static void logToBuffer( String s )
  {
    if ( bootstraplog == null )
      return;
    
    synchronized ( bootstraplog )
    {
      bootstraplog.append( s );
      bootstraplog.append( "\n" );
    }
  }

  /**
   * Logs a Throwable to the bootstrap log.
   * @param th 
   */
  private static void logToBuffer( Throwable th )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter( sw );
    th.printStackTrace( pw );
    WebAppCore.logToBuffer( sw.toString() );
    WebAppCore.logToBuffer( "\n" );
  }

  @Override
  public void consumeMessage( Message msg )
  {
    if ( !(msg instanceof TextMessage ) )
      return;
    TextMessage tm = (TextMessage)msg;
    try
    {
      String m = tm.getText();
      if ( "reconfigure".equals( m ) )
      {
        reloadSettings();
      }
    }
    catch (JMSException ex)
    {
      logger.error( "Unable to message other servers to reload properties.", ex );
    }
  }

  MimeMessage getBbEmail()
  {
    Properties bbprops = blackboard.platform.config.ConfigurationServiceFactory.getInstance().getBbProperties();
    String smtpHost = bbprops.getProperty("bbconfig.smtpserver.hostname");
    String dBsmtpHost = SystemRegistryUtil.getString("smtpserver_hostname", smtpHost);
    if (!StringUtils.isEmpty( dBsmtpHost ) && !"0.0.0.0".equals( dBsmtpHost ) )
      smtpHost = dBsmtpHost;
    if ( logger != null ) logger.debug( "Using " + smtpHost );
    
    Properties mailprops = new Properties();
    mailprops.setProperty("mail.smtp.host", smtpHost);
    Session mailSession = Session.getDefaultInstance(mailprops);
    
    return new MimeMessage(mailSession);
  }

  public void sendHtmlEmail(
          String subject, 
          InternetAddress from, 
          InternetAddress[] reply, 
          InternetAddress[] recipients, 
          InternetAddress[] courtesycopies, 
          String message) throws MessagingException
  {
    MimeMessage email = getBbEmail();
    MimeMultipart multipart = new MimeMultipart();
    BodyPart messageBodyPart = new MimeBodyPart();

    email.setSubject(subject);
    if ( reply != null && reply.length > 0 )
      email.setReplyTo( reply );
    email.setFrom( from );
    messageBodyPart.setContent(message, "text/html");
    multipart.addBodyPart(messageBodyPart);
    email.setRecipients( javax.mail.Message.RecipientType.TO, recipients );
    if ( courtesycopies != null && courtesycopies.length > 0 )
      email.setRecipients( javax.mail.Message.RecipientType.CC, courtesycopies );
    email.setContent(multipart);
    Transport.send(email);
  }
  
}


