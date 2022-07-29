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

import blackboard.platform.plugin.PlugInUtil;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;

/**
 * This servlet provides the user interface to BB system administrators.
 * Gives ability to reconfigure.
 * 
 * @author jon
 */
@WebServlet("/status/*")
public class StatusServlet extends HttpServlet
{  
  WebAppCore webappcore;
  
  DateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss z" );

  /**
   * Get a reference to the right instance of WebAppCore from an attribute which
   * that instance put in the servlet context.
   * @throws javax.servlet.ServletException
  */
  @Override
  public void init() throws ServletException
  {
    super.init();
    webappcore = (WebAppCore)getServletContext().getAttribute(WebAppCore.ATTRIBUTE_CONTEXTBBMONITOR );
  }
  
  public void sendError( HttpServletRequest req, HttpServletResponse resp, String error ) throws ServletException, IOException
  {
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body><p>" );
      out.println( error );
      out.println( "</p></body></html>" );
    }  
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    doProcessing( req, resp );
  }
  
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    doProcessing( req, resp );
  }  
  
  /**
   * Works out which page of information to present and calls the appropriate
   * method.
   * 
-   * @param req The request data.
   * @param resp The response data
   * @throws ServletException
   * @throws IOException 
   */
  protected void doProcessing(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    // Make sure that the user is authenticated and is a system admin.
    // Bail out if not.
    try
    {
      if ( !PlugInUtil.authorizeForSystemAdmin(req, resp) )
        return;
    }
    catch ( Exception e )
    {
      throw new ServletException( e );
    }

    // Which page is wanted?
    String setup = req.getParameter("setup");
    String setupsave = req.getParameter("setupsave");
    Config config = webappcore.getConfig();
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<title>LBU BB Upload Monitor</title>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "td { vertical-align: top-; padding: 0em 2em 0em 2em }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"index.html\">Home</a></p>" );      
      out.println( "<h1>LBU BB Upload Monitor</h1>" );
      
      try
      {
        if ( setup != null && setup.length() > 0)
          sendSetup( out, config );
        else if ( setupsave != null && setupsave.length() > 0)
          sendSetupSave( req, out, config );
        else
          sendBootstrap( out );
      }
      catch ( Exception e )
      {
        webappcore.logger.error( "Problem while serving HTML page.", e );
        throw new ServletException( "Problem while serving HTML page.", e );
      }
      
      out.println( "</body></html>" );
    }
  }
  
  
  /**
   * Output a list of log files that can be viewed or deleted.
   * @param out
   * @throws IOException 
   */
  void sendBootstrap( ServletOutputStream out ) throws IOException
  {
    out.println( "<h2>Bootstrap Log</h2>" );
    out.println( "<p>This bootstrap log comes from whichever server instance " +
                 "you are connected to and contains logging before the log file " +
                 "was initiated.</p>" );    
    out.println( "<pre>" );
    out.println( WebAppCore.getBootstrapLog() );
    out.println( "</pre>" );
  }
  
  
  /**
   * Send a form for settings.
   * 
   * @param out
   * @throws IOException 
   */
  void sendSetup( ServletOutputStream out, Config config ) throws IOException
  {
    String[][] sizes = 
    {
      {   "10",    "10MB" },
      {   "20",    "20MB" },
      {   "50",    "50MB" },
      {  "100",   "100MB" },
      {  "200",   "200MB" },
      {  "500",   "500MB" },
      { "1000", "1,000MB" },
      { "2000", "2,000MB" },
      { "5000", "5,000MB" }
    };
    Level[] levellist = { Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG };

    
    out.println( "<h2>Configure Settings</h2>" );
    out.println( "<p>Note: if you want to stop this building block plugin ");
    out.println( "running you should use the Building Blocks link in the ");
    out.println( "Integrations panel of the System Administration page and inactivate it.</p>" );
        
    out.println( "<form name=\"config\" action=\"status\" method=\"POST\">" );
    out.println( "<input type=\"hidden\" name=\"setupsave\" value=\"true\"/>" );

    out.println( "<h3>Technical Log</h3>" );
    out.println( "<p>How much detail do you want in the technical logs?</p>" );
    out.println( "<select name=\"loglevel\" size=\"" + levellist.length + "\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (level.equals(config.getLoglevel())?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );

    out.println( "<h3>User Name</h3>" );
    out.println( "<p>Username that will be used for all Xythos file system operations. Requires restart if changed.</p>" );
    out.println( "<input name=\"username\" value=\"" + config.getUserName() + "\"/>" );
    
    out.println( "<h3>Email address in from field</h3>" );
    out.println( "<input name=\"emailfrom\" value=\"" + config.getEmailFrom() + "\"/>" );
    out.println( "<h3>Name for Above Address</h3>" );
    out.println( "<input name=\"emailfromname\" value=\"" + config.getEmailFromName() + "\"/>" );

    
    for ( int i=0; i<config.rules.size(); i++ )
    {
      RuleConfig rule = config.getRules().get( i );
      
      out.println( "<hr />" );
      out.println( "<h2>Rule " + (i+1) + "</h3>" );
      
      out.println( "<h3>Name" );
      out.println( "<input name=\"name_" + i + "\" value=\"" + rule.getName() + "\"/>" );

      out.println( " Enabled" );
      out.println( "<input value=\"true\" type=\"checkbox\" name=\"enabled_" + i + "\" " + (rule.isEnabled()?"checked=\"true\"":"") + "/></h3>" );
      
      out.println( "<h3>Filters</h3>" );
      out.println( "<table border=\"0\"><tr><td>");
      out.println( "<h4>File Size Threshold</h4>" );
      out.println( "<p>How big does a file have to be to take action?</p>" );
      out.println( "<select name=\"filesize_" + i + "\" size=\"" + sizes.length + "\">" );
      String s = Integer.toString( rule.getFileSize() );
      for ( String[] pair : sizes )
        out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(s)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
      out.println( "</select>" );
      
      out.println( "</td><td>");
      out.println( "<h4>Admin Only" );
      out.println( "<input value=\"true\" type=\"checkbox\" name=\"adminonly_" + i + "\" " + (rule.isAdminOnly()?"checked=\"true\"":"") + "/></h4>" );

      out.println( "<h4>MIME Type Filter</h4>" );
      out.println( "<p>(Java regular expression)</p>" );
      out.println( "<input name=\"typeregex_" + i + "\" value=\"" + rule.getTypeRegex() + "\"/>" );

      out.println( "<h4>File Path Filter</h4>" );
      out.println( "<p>(Java regular expression)</p>" );
      out.println( "<input name=\"pathregex_" + i + "\" value=\"" + rule.getPathRegex() + "\"/>" );
      out.println( "</td></tr></table>");

      out.println( "<h3>Actions</h3>" );
      out.println( "<table><tr>" );
      out.println( "<td><input value=\"true\" type=\"checkbox\" name=\"actionlog_"       + i + "\" " + (rule.isActionLog()      ?"checked=\"true\"":"") + "/> Log</td>" );
      out.println( "<td><input value=\"true\" type=\"checkbox\" name=\"actionemail_"     + i + "\" " + (rule.isActionEmail()    ?"checked=\"true\"":"") + "/> Email</td>" );
      out.println( "<td><input value=\"true\" type=\"checkbox\" name=\"actionoverwrite_" + i + "\" " + (rule.isActionOverwrite()?"checked=\"true\"":"") + "/> Overwrite</td>" );
      out.println( "</tr><tr><td></td><td>" );
      
      out.println( "<h4>EMail</h4>" );
      out.println( "<p>What subject line should the email have?</p>" );
      out.println( "<input name=\"emailsubject_" + i + "\" value=\"" + rule.getEmailSubject() + "\"/>" );
      out.println( "<p>What message should be sent to the user?</p>" );
      out.println( "<textarea name=\"emailbody_" + i + "\" cols=\"40\" rows=\"10\">" + rule.getEmailBody() + "</textarea>" );
      out.println( "</td><td>" );
      out.println( "<h4>Overwrite Source File</h4>" );
      out.println( "<p>Full path relative to webdav base.</p>" );
      out.println( "<input name=\"overwritepath_" + i + "\" value=\"" + rule.getOverwritePath() + "\"/>" );
      out.println( "</td></tr></table>");
      
      out.println( "<h4>Continue</h4><p>If filter doesn't match, processing always moves to the next rule. If it does match, continuation is optional.</p>" );
      out.println( "<p><input value=\"true\" type=\"checkbox\" name=\"continuerules_" + i + "\" " + (rule.isContinueRules()?"checked=\"true\"":"") + "/>Enable</p>" );
    }
    
    out.println( "<h3>Submit</h3>" );
    out.println( "<p><input type=\"submit\" value=\"Save\"/></p>" );
    out.println( "</form>" );
  }

  void sendSetupSave( HttpServletRequest req, ServletOutputStream out, Config config ) throws IOException
  {
    Config newconfig = new Config();
    
    String loglevel             = req.getParameter( "loglevel"         );
    String username             = req.getParameter( "username"         );
    String emailfrom            = req.getParameter( "emailfrom"        );
    String emailfromname        = req.getParameter( "emailfromname"    );

    newconfig.setLoglevelName( loglevel );
    newconfig.setUserName( username );
    newconfig.setEmailFrom( emailfrom );
    newconfig.setEmailFromName (emailfromname );

    for ( int i=0; i<newconfig.rules.size(); i++ )
    {
      RuleConfig rule = newconfig.getRules().get( i );
      
      String name                 = req.getParameter( "name_"            + i );
      String enabled              = req.getParameter( "enabled_"         + i );
      String actionlog            = req.getParameter( "actionlog_"       + i );
      String actionemail          = req.getParameter( "actionemail_"     + i );
      String actionoverwrite      = req.getParameter( "actionoverwrite_" + i );
      
      String filesize             = req.getParameter( "filesize_"        + i );
      if ( StringUtils.isEmpty( filesize ) ) filesize = "5000";
      String adminonly            = req.getParameter( "adminonly_"       + i );
      String typeregex            = req.getParameter( "typeregex_"       + i );
      String pathregex            = req.getParameter( "pathregex_"       + i );
      
      String emailsubject         = req.getParameter( "emailsubject_"    + i );
      String emailbody            = req.getParameter( "emailbody_"       + i );
      String overwritepath        = req.getParameter( "overwritepath_"   + i );

      String continuerules        = req.getParameter( "continuerules_"   + i );
      
      rule.setN( i );
      rule.setName( name );
      rule.setEnabled(         "true".equals( enabled         ) );
      rule.setActionLog(       "true".equals( actionlog       ) );
      rule.setActionEmail(     "true".equals( actionemail     ) );
      rule.setActionOverwrite( "true".equals( actionoverwrite ) );
      rule.setAdminOnly(       "true".equals( adminonly       ) );
      rule.setFileSize(        Integer.parseInt(filesize )      );
      rule.setTypeRegex(       typeregex                        );
      rule.setPathRegex(       pathregex                        );
      rule.setEmailSubject(    emailsubject                     );
      rule.setEmailBody(       emailbody                        );
      rule.setOverwritePath(   overwritepath                    );
      rule.setContinueRules(   "true".equals( continuerules   ) );
    }
    
    out.println( "<h2>Saving Configuration Settings</h2>" );

    try
    {
      webappcore.saveConfig( newconfig );
    }
    catch ( Throwable th )
    {
      webappcore.logger.error( "Unable to save properties.", th );
      out.println( "<p>Technical problem trying to save settings</p>" );
      return;
    }
    out.println( "<p>Saved settings</p>" );
  }  
  
}
