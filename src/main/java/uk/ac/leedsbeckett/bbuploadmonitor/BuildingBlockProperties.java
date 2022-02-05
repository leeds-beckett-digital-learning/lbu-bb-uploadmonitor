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

import java.util.Properties;
import org.apache.log4j.Level;

/**
 *
 * @author jon
 */
public class BuildingBlockProperties extends Properties
{
  public BuildingBlockProperties(Properties defaults)
  {
    super(defaults);
  }
  public Level getLogLevel()
  {
    return Level.toLevel( getProperty("loglevel") );
  }
  public void setLogLevel( Level level )
  {
    setProperty( "loglevel", level.toString() );
  }
  public String getUsername()
  {
    return getProperty( "username" );
  }
  public void setUsername( String username )
  {
    setProperty( "username", username );
  }  
  public int getFileSize()
  {
    return Integer.parseInt( getProperty("filesize") );
  } 
  public void setFileSize( int fs )
  {
    setProperty( "filesize", Integer.toString( fs ) );
  }
  public String getAction()
  {
    return getProperty( "action" );
  }
  public void setAction( String action )
  {
    setProperty( "action", action );
  }
  public String getEMailSubject()
  {
    return getProperty( "emailsubject" );
  }
  public void setEMailSubject( String emailsubject )
  {
    setProperty( "emailsubject", emailsubject );
  }
  public String getFileMatchingExpression()
  {
    return getProperty( "filematchingexpression" );
  }
  public void setFileMatchingExpression( String filematchingexpression )
  {
    setProperty( "filematchingexpression", filematchingexpression );
  }
  public String getEMailBody()
  {
    return getProperty( "emailbody" );
  }
  public void setEMailBody( String emailbody )
  {
    setProperty( "emailbody", emailbody );
  }
  public String getSpecialEMailBody()
  {
    return getProperty( "specialemailbody" );
  }
  public void setSpecialEMailBody( String specialemailbody )
  {
    setProperty( "specialemailbody", specialemailbody );
  }
  public String getEMailFrom()
  {
    return getProperty( "emailfrom" );
  }
  public void setEMailFrom( String emailfrom )
  {
    setProperty( "emailfrom", emailfrom );
  }
  public String getEMailFromName()
  {
    return getProperty( "emailfromname" );
  }
  public void setEMailFromName( String emailfromname )
  {
    setProperty( "emailfromname", emailfromname );
  }
}
