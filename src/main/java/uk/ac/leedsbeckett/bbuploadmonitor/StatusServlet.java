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
import org.apache.log4j.Level;

/**
 * This servlet provides the user interface to BB system administrators.
 * Gives access to logs and ability to reconfigure.
 * 
 * @author jon
 */
@WebServlet("/status/*")
public class StatusServlet extends HttpServlet
{  
  WebAppCore bbmonitor;
  
  DateFormat df = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss z" );

  /**
   * Get a reference to the right instance of WebAppCore from an attribute which
   * that instance put in the servlet context.
  */
  public void init() throws ServletException
  {
    super.init();
    bbmonitor = (WebAppCore)getServletContext().getAttribute(WebAppCore.ATTRIBUTE_CONTEXTBBMONITOR );
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
  
  /**
   * Works out which page of information to present and calls the appropriate
   * method.
   * 
   * @param req The request data.
   * @param resp The response data
   * @throws ServletException
   * @throws IOException 
   */
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
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
    BuildingBlockProperties props = bbmonitor.getProperties();
    
    resp.setContentType("text/html");
    try ( ServletOutputStream out = resp.getOutputStream(); )
    {
      out.println( "<!DOCTYPE html>\n<html>" );
      out.println( "<head>" );
      out.println( "<title>LBU BB Upload Monitor</title>" );
      out.println( "<style type=\"text/css\">" );
      out.println( "body, p, h1, h2 { font-family: sans-serif; }" );
      out.println( "</style>" );
      out.println( "</head>" );
      out.println( "<body>" );
      out.println( "<p><a href=\"index.html\">Home</a></p>" );      
      out.println( "<h1>LBU BB Upload Monitor</h1>" );
      
      if ( setup != null && setup.length() > 0)
        sendSetup( out, props );
      else if ( setupsave != null && setupsave.length() > 0)
        sendSetupSave( req, out, props );
      else
        sendBootstrap( out );
      
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
  void sendSetup( ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String s = Integer.toString( props.getFileSize() );
    String[][] sizes = 
    {
      {  "100",  "100MB" },
      {  "250",  "250MB" },
      {  "500",  "500MB" },
      { "1024", "1024MB" },
      { "2048", "2048MB" },
      { "5120", "5120MB" }
    };
    String sa = props.getAction();
    String[][] actions = 
    {
      { "none", "None" },
      { "mode1", "Mode 1 Email Notification" },
      { "mode1a", "Mode 1 Email Notification (*admin users only)" }
    };
    Level[] levellist = { Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG };
    Level currentlevel = props.getLogLevel();
    
    out.println( "<h2>Configure Settings</h2>" );
    out.println( "<p>Note: if you want to stop this building block plugin ");
    out.println( "running you should use the Building Blocks link in the ");
    out.println( "Integrations panel of the System Administration page and inactivate it.</p>" );
        
    out.println( "<form name=\"config\" action=\"status\" method=\"GET\">" );
    out.println( "<input type=\"hidden\" name=\"setupsave\" value=\"true\"/>" );

    out.println( "<h3>Technical Log</h3>" );
    out.println( "<p>How much detail do you want in the technical logs?</p>" );
    out.println( "<table><tr><td>Main Log<br/>" );
    out.println( "<select name=\"loglevel\" size=\"4\">" );
    for ( Level level : levellist )
      out.println( "  <option value=\"" + level.toString() + "\"" + (currentlevel.equals(level)?" selected=\"true\"":"") + ">" + level.toString() + "</option>" );
    out.println( "</select>" );
    out.println( "</td></tr></table>" );

    out.println( "<h3>Big File Log</h3>" );
    out.println( "<p>How big does a file have to be to record it in the log when it is created?</p>" );
    out.println( "<select name=\"filesize\" size=\"6\">" );
    for ( String[] pair : sizes )
      out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(s)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
    out.println( "</select>" );
    out.println( "<h3>Action</h3>" );
    out.println( "<p>What action should be taken?</p>" );
    out.println( "<select name=\"action\" size=\"4\">" );
    for ( String[] pair : actions )
      out.println( "  <option value=\"" + pair[0] + "\"" + (pair[0].equals(sa)?" selected=\"true\"":"") + ">" + pair[1] + "</option>" );
    out.println( "</select>" );
    
    out.println( "<h3>EMail</h3>" );
    out.println( "<p>What email address should notifications be sent from?</p>" );
    out.println( "<input name=\"emailfrom\" value=\"" + props.getEMailFrom() + "\"/>" );
    out.println( "<p>What human readble name goes with that address?</p>" );
    out.println( "<input name=\"emailfromname\" value=\"" + props.getEMailFromName() + "\"/>" );
    out.println( "<p>What subject line should the email have?</p>" );
    out.println( "<input name=\"emailsubject\" value=\"" + props.getEMailSubject() + "\"/>" );
    out.println( "<p>What message should be sent to users when they upload a huge video file?</p>" );
    out.println( "<textarea name=\"emailbody\" cols=\"40\" rows=\"10\">" + props.getEMailBody() + "</textarea>" );
    
    out.println( "<p>Which files should also be overwritten soon after upload? (regular expression)</p>" );
    out.println( "<input name=\"regex\" value=\"" + props.getFileMatchingExpression() + "\"/>" );
    out.println( "<p>What message should be sent to users for the matching files?</p>" );
    out.println( "<textarea name=\"specialemailbody\" cols=\"40\" rows=\"10\">" + props.getSpecialEMailBody() + "</textarea>" );
    
    out.println( "<h3>Submit</h3>" );
    out.println( "<p><input type=\"submit\" value=\"Save\"/></p>" );
    out.println( "</form>" );
  }

  void sendSetupSave( HttpServletRequest req, ServletOutputStream out, BuildingBlockProperties props ) throws IOException
  {
    String filesize         = req.getParameter( "filesize"         );
    String loglevel         = req.getParameter( "loglevel"         );
    String action           = req.getParameter( "action"           );
    String emailsubject     = req.getParameter( "emailsubject"     );
    String emailbody        = req.getParameter( "emailbody"        );
    String regex            = req.getParameter( "regex"            );
    String specialemailbody = req.getParameter( "specialemailbody" );
    String emailfrom        = req.getParameter( "emailfrom"        );
    String emailfromname    = req.getParameter( "emailfromname"    );
    
    out.println( "<h2>Saving Configuration Settings</h2>" );

    try
    {
      props.setLogLevel(   Level.toLevel(  loglevel ) );
      props.setFileSize( Integer.parseInt( filesize ) );
      props.setAction( action );
      props.setEMailSubject( emailsubject );
      props.setFileMatchingExpression( regex );
      props.setEMailBody( emailbody );
      props.setSpecialEMailBody( specialemailbody );
      props.setEMailFrom( emailfrom );
      props.setEMailFromName( emailfromname );
      bbmonitor.saveProperties();
    }
    catch ( Throwable th )
    {
      bbmonitor.logger.error( "Unable to save properties.", th );
      out.println( "<p>Technical problem trying to save settings</p>" );
      return;
    }
    out.println( "<p>Saved settings</p>" );
  }  
  
}
