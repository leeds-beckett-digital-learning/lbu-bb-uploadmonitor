/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbuploadmonitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import org.apache.log4j.Level;

/**
 *
 * @author jon
 */
public class Config
{
  @JsonIgnore
  Level loglevel = Level.INFO;
  
  String loglevelname = Level.INFO.toString();
  String userName = "administrator";
  String emailFrom = "";
  String emailFromName = "";
  ArrayList<RuleConfig> rules = new ArrayList<>();

  public Config()
  {
    for ( int i=0; i<5; i++ )
    {
      rules.add( new RuleConfig() );
      rules.get( i ).setN( i );
    }
  }
  
  public Level getLoglevel() {
    return loglevel;
  }

  public String getLoglevelName()
  {
    return loglevelname;
  }

  public void setLoglevelName( String loglevelname )
  {
    this.loglevelname = loglevelname;
    this.loglevel = Level.toLevel( loglevelname );
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getEmailFrom() {
    return emailFrom;
  }

  public void setEmailFrom(String emailFrom) {
    this.emailFrom = emailFrom;
  }

  public String getEmailFromName() {
    return emailFromName;
  }

  public void setEmailFromName(String emailFromName) {
    this.emailFromName = emailFromName;
  }

  
  
  public ArrayList<RuleConfig> getRules()
  {
    return rules;
  }

  public void setRules(ArrayList<RuleConfig> rules)
  {
    this.rules = rules;
  }
  
}
