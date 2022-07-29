/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uk.ac.leedsbeckett.bbuploadmonitor;

import blackboard.platform.impl.services.task.TaskException;
import com.xythos.common.api.VirtualServer;
import com.xythos.common.api.XythosException;
import com.xythos.fileSystem.DirectoryEntry;
import com.xythos.fileSystem.File;
import com.xythos.security.api.Context;
import com.xythos.storageServer.admin.api.AdminUtil;
import com.xythos.storageServer.api.FileSystem;
import com.xythos.storageServer.api.FileSystemEntry;
import com.xythos.storageServer.api.StorageServerException;
import java.util.ArrayList;

/**
 * This class overwrites Xythos files with another Xythos file and does
 * that in a background thread.
 * 
 * @author jon
 */
public class FileProcessWorker implements Runnable
{
  public static final int MINIMUM_AGE_MILLIS = 1000*60;
  Thread worker;
  WebAppCore webappcore;

  final ArrayList<Entry> pending = new ArrayList<Entry>();
  
  public FileProcessWorker( WebAppCore webappcore )
  {
    this.webappcore = webappcore;
  }

  /**
   * Called from other thread to push an instruction to overwrite a file
   * into a to do list.
   * 
   * 
   * @param targetpath The Xythos file to overwrite.
   * @param sourcepath The Xythos file to overwrite with.
   * @param vs The Xythos server ID where both files are located.
   */
  public void add( String targetpath, String sourcepath, VirtualServer vs )
  {
    synchronized( pending )
    {
      Entry entry = new Entry();
      entry.sourcepath = sourcepath;
      entry.targetpath = targetpath;
      entry.vs = vs;
      entry.timestamp = System.currentTimeMillis();
      pending.add( entry );
    }
  }
  
  /**
   * Called from inside the worker thread to get the next instruction to
   * work on. Requires that a path to be a minimum age before popping.
   * @return 
   */
  Entry pop()
  {
    synchronized( pending )
    {
      long now = System.currentTimeMillis();
      for ( int i=0; i<pending.size(); i++ )
      {
        Entry e = pending.get( i );
        long age = now - e.timestamp;
        if ( age > MINIMUM_AGE_MILLIS )
        {
          pending.remove( i );
          return e;
        }
      }
      return null;
    }    
  }

  /**
   * Called once to start the thread that does the work.
   */
  public void start()
  {
    if ( worker != null )
      throw new IllegalArgumentException( "Thread already started." );
    webappcore.logger.info( "FileProcessWorker is starting its thread." );
    worker = new Thread( this );
    worker.start();
  }
  
  public void interrupt()
  {
    if ( worker != null )
      worker.interrupt();
  }
  
  /**
   * The standard thread run method.
   */
  @Override
  public void run()
  {
    try
    {
      webappcore.logger.info( "FileProcessWorker has started." );
      process();
    }
    catch ( Throwable t )
    {
      webappcore.logger.info( "Exception stopped the FileProcessWorker.", t );     
    }
    finally
    {
      worker = null;    
    }
    webappcore.logger.info( "FileProcessWorker has stopped." );
  }
  
  /**
   * Called by run method. Has the main loop.
   */
  public void process()
  {
    try { Thread.sleep( 5000 ); } catch (InterruptedException ex) {}
    while ( !worker.isInterrupted() )
    {    
      // Clear the queue
      Entry entry=null;
      while ( (entry = pop()) != null )
      {
        webappcore.logger.debug( "Processing {" + entry.targetpath + "}" );
        if ( entry.sourcepath != null && entry.sourcepath.length() > 0 )
        {
          try
          {
            overwriteOneHugeFile(entry.targetpath, entry.sourcepath, entry.vs );
          }
          catch ( Exception ex )
          {
            webappcore.logger.error( "Exception while attempting to overwrite file.", ex );
          }
        }
      }
      
      // Be kind to the CPU
      if ( entry == null )
      {
        try { Thread.sleep( 60000 ); } catch (InterruptedException ex) {}
        webappcore.logger.debug( "FileProcessWorker woke up." );
      }
    }
    webappcore.logger.info( "FileProcessWorker thread ending due to thread interruption." );
  }
  
  void overwriteOneHugeFile( String targetpath, String sourcepath, VirtualServer vs ) throws StorageServerException, XythosException, TaskException
  {
    webappcore.logger.info( "Overwriting " + targetpath );
    Context context=null;
    
    try
    {
      
      context = AdminUtil.getContextForAdmin( "FileProcessWorker" );
      if ( context == null )
      {
        webappcore.logger.error( "Unable to obtain Xythos context for admin.\n" );
        throw new TaskException( "Unable to obtain Xythos context for admin.\n" );
      }
      
      String path = targetpath;
      int n = path.lastIndexOf( "/" );
      String destinationdir = path.substring( 0, n );
      String destinationname = path.substring( n+1 );
      
      
      FileSystemEntry sourcefile = FileSystem.findEntry( vs, sourcepath, false, context );
      if ( sourcefile == null )
      {
        webappcore.logger.error( "File not found: " + sourcepath + " on Xythos virtual server " + vs.getName() );
        return;
      }
      webappcore.logger.info( "Copying " + sourcefile.getName() + " over " + targetpath );
      DirectoryEntry de = (DirectoryEntry)sourcefile;
      File f = (File)sourcefile;
      int version = f.getFileVersion();

      DirectoryEntry newentry = de.copyNode( 
              version,                       // version number of source to copy
              vs,                            // virtual server
              destinationdir,                // destination dir
              destinationname,               // (new) name
              de.getCreatedByPrincipalID(),  // same owner as source
              2,                             // webdav depth; 2 means infinite which is default depth
              true,                          // overwrite 
              DirectoryEntry.TRASH_OP.NONE,  // no trash operation
              false                          // not move, copy
      );

      // Renaming is done with 'move' using same parent directory
      // This should preserve the file ID and should not break links to
      // the original. It is done so that users understand that this is 
      // a different file.
      newentry.move( destinationdir, "video_removed_" + System.currentTimeMillis() + "_" + destinationname, false );
    }
    catch ( XythosException th )
    {
      webappcore.logger.error( "Error occured looking for files in course directory.", th);
      if ( context != null )
      {
        try { context.rollbackContext(); }
        catch ( XythosException ex ) { webappcore.logger.error( "Failed to roll back Xythos context.", ex ); }
      }
    }
    finally
    {
      if ( context != null )
      {
       try { context.commitContext(); }
        catch ( XythosException ex ) { webappcore.logger.error( "Failed to commit Xythos context.", ex ); }
      }
    }
  }

  
  
  class Entry
  {
    String sourcepath;
    String targetpath;
    VirtualServer vs;
    long timestamp;
  }
}
