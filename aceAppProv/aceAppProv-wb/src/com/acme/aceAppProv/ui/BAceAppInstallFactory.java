/*
 * Copyright 2022 Tridium, Inc. All Rights Reserved.
 */

package com.acme.aceAppProv.ui;

import javax.baja.batchJob.BBatchJob;
import javax.baja.batchJob.BJobStep;
import javax.baja.file.BDirectory;
import javax.baja.file.BIFile;
import javax.baja.file.FilePath;
import javax.baja.naming.BOrd;
import javax.baja.naming.OrdQuery;
import javax.baja.naming.UnresolvedException;
import javax.baja.nre.annotations.AgentOn;
import javax.baja.nre.annotations.NiagaraSingleton;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.provisioningNiagara.ui.BStationStepFactory;
import javax.baja.space.Mark;
import javax.baja.sys.BComponent;
import javax.baja.sys.BObject;
import javax.baja.sys.BString;
import javax.baja.sys.BasicContext;
import javax.baja.sys.Context;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.ui.BDialog;
import javax.baja.ui.BWidget;
import javax.baja.ui.file.BFileChooser;
import javax.baja.ui.file.ExtFileFilter;

import com.acme.aceAppProv.BAceAppInstallStep;

/**
 * Provisioning UI factory for BAceAppInstallStep
 *
 */
@NiagaraType(agent = @AgentOn(types={"aceAppProv:AceAppInstallStep"}))
@NiagaraSingleton

public class BAceAppInstallFactory
  extends BStationStepFactory
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.acme.aceAppProv.ui.BAceAppInstallFactory(755933741)1.0$ @*/
/* Generated Fri Feb 18 12:14:28 EST 2022 by Slot-o-Matic (c) Tridium, Inc. 2012 */
  
  public static final BAceAppInstallFactory INSTANCE = new BAceAppInstallFactory();

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BAceAppInstallFactory.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  @Override
  public BJobStep makeStep(BWidget owner, BBatchJob batchJob, BObject jobTarget, BObject source, Context context)
    throws Exception
  {
    // Prompt for the ACE file
    BFileChooser chooser = BFileChooser.makeOpen(owner);
    chooser.setCurrentDirectory(BOrd.make("local:|file:~/ace"));
    chooser.clearFilters();
    chooser.addFilter(new ExtFileFilter(BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.fileFilter"), "ace"));
    chooser.setUpdateFilenameExtensionFromFilter(true);
    BOrd fileOrd = chooser.show();
    if (fileOrd == null)
    {
      // The file chooser was canceled, so bail
      return null;
    }

    // Display warning that running this job will overwrite any existing ACE application
    if (BDialog.CANCEL == BDialog.open(owner, BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.warningTitle"),
      BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.deployWarning") + "\n\n" +
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.backupWarning"),
      BDialog.OK_CANCEL, BDialog.WARNING_ICON, (String)null))
    {
      return null;
    }

    // Copy the ACE file to the supervisor.
    try
    {
      BIFile file = (BIFile)fileOrd.resolve().get();
      BObject cacheDir = getTargetDir(BOrd.make(BAceAppInstallStep.ACE_CACHE), jobTarget);
      if (!deleteExistingFile(file.getFileName(), (BDirectory)cacheDir, owner))
      {
        return null;
      }

      Mark mark =  new Mark((BObject)file, file.getFileName());
      mark.copyTo(cacheDir, new BComponent(), new BasicContext());

      // Show confirmation of file copy
      BDialog.info(owner,
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.copyAceFile.complete"),
        BString.make(BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.copyAceFile.completeDetail",
          file.getFileName(), BAceAppInstallStep.ACE_CACHE)));

      // Create the job step
      BAceAppInstallStep step = new BAceAppInstallStep();
      step.setAceFileName(file.getFileName());
      return step;
    }
    catch (Exception e)
    {
      BDialog.error(owner,
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.copyAceFile.error"),
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.copyAceFile.errorDetail"),
        e);
    }

    // Something went wrong if we get here
    return null;
  }

  // Delete the ACE file if it exists, prompting the user to confirm the file overwrite
  private boolean deleteExistingFile(String filenameToDelete, BDirectory cacheDirectory, BWidget owner)
  {
    // Check for an existing file of the same name
    BIFile fileToDelete = null;
    BIFile[] cacheFiles = cacheDirectory.listFiles();
    for (BIFile file : cacheFiles)
    {
      if (file.getFileName().equalsIgnoreCase(filenameToDelete))
      {
        fileToDelete = file;
        break;
      }
    }

    if (fileToDelete != null)
    {
      // Allow the user to confirm that this file will overwrite the existing cached file on the supervisor
      if (BDialog.CANCEL == BDialog.open(owner,
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.deleteExisting.title"),
        BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.deleteExisting.message", filenameToDelete),
        BDialog.OK_CANCEL,
        BDialog.WARNING_ICON,
        (String)null))
      {
        return false;
      }

      // Delete the file
      try
      {
        fileToDelete.delete();
      }
      catch (Exception e)
      {
        BDialog.error(owner,
          BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.deleteExisting.title"),
          BAceAppInstallStep.lexicon.getText("AceAppInstallFactory.deleteExisting.error", filenameToDelete),
          e);
        return false;
      }
    }

    return true;
  }

  // Resolve a directory on the target station, creating any missing folders in the path
  private BObject getTargetDir(BOrd ord, BObject target)
    throws Exception
  {
    BObject obj;
    try
    {
      // Attempt to resolve target ord
      obj = ord.resolve(target).get();
    }
    catch(UnresolvedException e)
    {
      // Attempt to create any missing file directories
      OrdQuery[] oqs = ord.parse();
      for (OrdQuery oq : oqs)
      {
        if (!(oq instanceof FilePath))
        {
          continue;
        }
        FilePath fp = (FilePath)oq;
        BDirectory rootDir = (BDirectory)BOrd.make("file:^").resolve(target).get();
        rootDir.getFileSpace().makeDir(fp);
      }

      // Try again
      obj = ord.resolve(target).get();
    }
    return obj;
  }

}

