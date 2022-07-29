/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbuploadmonitor;

/**
 * POJO containing properties of a rule.
 * @author jon
 */
public class RuleConfig
{
  int     n               = 0;
  boolean enabled         = false;
  String  name            = "";
  boolean actionLog       = false;
  boolean actionEmail     = false;
  boolean actionOverwrite = false;
  
  int     fileSize        = 5000;  // In MB
  boolean adminOnly       = false;
  String  typeRegex       = "";
  String  pathRegex       = "";
  
  String  emailSubject    = "";
  String  emailBody       = "";
  String  overwritePath   = "";
  
  boolean continueRules   = false;
  

  public int getN() {
    return n;
  }

  public void setN(int n) {
    this.n = n;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActionLog() {
    return actionLog;
  }

  public void setActionLog(boolean actionLog) {
    this.actionLog = actionLog;
  }
  
  public boolean isActionEmail() {
    return actionEmail;
  }

  public void setActionEmail(boolean actionEmail) {
    this.actionEmail = actionEmail;
  }

  public boolean isActionOverwrite() {
    return actionOverwrite;
  }

  public void setActionOverwrite(boolean actionOverwrite) {
    this.actionOverwrite = actionOverwrite;
  }

  public int getFileSize() {
    return fileSize;
  }

  public void setFileSize(int fileSize) {
    this.fileSize = fileSize;
  }

  public boolean isAdminOnly() {
    return adminOnly;
  }

  public void setAdminOnly(boolean adminOnly) {
    this.adminOnly = adminOnly;
  }

  public String getTypeRegex() {
    return typeRegex;
  }

  public void setTypeRegex(String typeRegex) {
    this.typeRegex = typeRegex;
  }

  public String getPathRegex() {
    return pathRegex;
  }

  public void setPathRegex(String pathRegex) {
    this.pathRegex = pathRegex;
  }

  public String getEmailSubject() {
    return emailSubject;
  }

  public void setEmailSubject(String emailSubject) {
    this.emailSubject = emailSubject;
  }

  public String getEmailBody() {
    return emailBody;
  }

  public void setEmailBody(String emailBody) {
    this.emailBody = emailBody;
  }

  public String getOverwritePath() {
    return overwritePath;
  }

  public void setOverwritePath(String overwritePath) {
    this.overwritePath = overwritePath;
  }

  public boolean isContinueRules() {
    return continueRules;
  }

  public void setContinueRules(boolean continueRules) {
    this.continueRules = continueRules;
  }
  
  
}
