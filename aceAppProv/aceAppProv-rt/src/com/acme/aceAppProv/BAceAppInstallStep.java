/*
 * Copyright 2022 Tridium, Inc. All Rights Reserved.
 */

package com.acme.aceAppProv;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

import javax.baja.batchJob.BBatchJobService;
import javax.baja.batchJob.driver.BDeviceJobStep;
import javax.baja.batchJob.driver.BDeviceStepDetails;
import javax.baja.batchJob.driver.DeviceNetworkJobOp;
import javax.baja.driver.BDevice;
import javax.baja.file.BIFile;
import javax.baja.job.BJobState;
import javax.baja.naming.BOrd;
import javax.baja.nre.annotations.NiagaraProperty;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.BBlob;
import javax.baja.sys.BComponent;
import javax.baja.sys.BObject;
import javax.baja.sys.BString;
import javax.baja.sys.Context;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.util.Lexicon;

import com.tridium.ace.BAceDevice;
import com.tridium.ace.datatypes.BAceDownloadParams;
import com.tridium.ace.program.BAceAppDownloadJob;
import com.tridium.ace.sys.BAceAppFile;
import com.tridium.driver.util.DrUtil;
import com.tridium.driver.util.StringUtil;
import com.tridium.fox.sys.BFoxSession;
import com.tridium.nre.security.KeyStorePermission;
import com.tridium.provisioningNiagara.ProvisioningConnectionUtil;
import com.tridium.util.CompUtil;

/**
 * Install an ACE application file to a target
 */
@NiagaraType
@NiagaraProperty
  (
    name = "aceFileName",
    type = "String",
    defaultValue = ""
  )
public class BAceAppInstallStep
  extends BDeviceJobStep
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.acme.aceAppProv.BAceAppInstallStep(3553532836)1.0$ @*/
/* Generated Fri Feb 18 12:14:27 EST 2022 by Slot-o-Matic (c) Tridium, Inc. 2012 */

////////////////////////////////////////////////////////////////
// Property "aceFileName"
////////////////////////////////////////////////////////////////
  
  /**
   * Slot for the {@code aceFileName} property.
   * @see #getAceFileName
   * @see #setAceFileName
   */
  public static final Property aceFileName = newProperty(0, "", null);
  
  /**
   * Get the {@code aceFileName} property.
   * @see #aceFileName
   */
  public String getAceFileName() { return getString(aceFileName); }
  
  /**
   * Set the {@code aceFileName} property.
   * @see #aceFileName
   */
  public void setAceFileName(String v) { setString(aceFileName, v, null); }

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BAceAppInstallStep.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  @Override
  protected void doRun(BBatchJobService svc, BDeviceStepDetails details, BDevice device, DeviceNetworkJobOp op)
    throws Exception
  {
    details.message("aceAppProv", "AceAppInstallStep.start", new String[] {device.getName()});

    // Get an instance of BAceAppFile on the supervisor to install
    BIFile aceFile = (BIFile)BOrd.make(ACE_CACHE + '/' + getAceFileName()).resolve().get();
    if (!(aceFile instanceof BAceAppFile))
    {
      details.vaFailed("AceAppInstallStep.failed.aceFileNotFound", getAceFileName());
      details.complete(BJobState.failed);
      return;
    }

    // Verify the BAceDevice on the target station
    try (ProvisioningConnectionUtil util = new ProvisioningConnectionUtil(device, details))
    {
      BFoxSession foxSession = util.getEngagedFoxSession();
      BObject target = null;
      try
      {
        target = getTargetObject(foxSession);
      }
      catch (Exception e)
      {
        details.failed("aceAppProv", "AceAppInstallStep.failed.aceDeviceNotFound", device.getName(), e);
        details.complete(BJobState.failed);
        return;
      }

      if (!(target instanceof BAceDevice))
      {
        details.vaFailed("AceAppInstallStep.failed.notAceDevice", device.getName());
        details.complete(BJobState.failed);
        return;
      }

      // Create a BAceDownloadParams to include the contents of the ACE file
      BAceDevice aceDevice = (BAceDevice)target;
      byte[] a = aceFile.read();
      BBlob blob = BBlob.make(a);
      BOrd[] ords = {aceDevice.getHandleOrd()};
      String ordString = StringUtil.toString(ords);
      BAceDownloadParams params = new BAceDownloadParams(ordString, "app.ace", blob, true);

      // Create and run a BAceAppDownloadJob on the target station
      BOrd jobOrd = aceDevice.getAceNetwork().downloadApp(params);
      BAceAppDownloadJob job = null;
      try
      {
        aceDevice.getComponentSpace().sync();
        job = (BAceAppDownloadJob)jobOrd.get(aceDevice);
      }
      catch(Exception e)
      {
        details.vaFailed("AceAppInstallStep.failed.installJobStartFailed", device.getName());
        details.complete(BJobState.failed);
        return;
      }
      // Lease the job on the target station so we can get the status as it runs
      job.lease();
      details.message("aceAppProv", "AceAppInstallStep.downloadRunning",
        new String[] {jobOrd.encodeToString(), job.getJobState().getTag()});
      waitForJobComplete(job);

      // Check if any modules are missing or other failure occurred
      if(job.getJobState() != BJobState.success)
      {
        details.message("aceAppProv", "AceAppInstallStep.failed.jobState", job.getJobState().getTag());

        // Job failed, first check if there are missing modules
        BString missingModules = job.getMissingModules();
        if(missingModules != null)
        {
          details.vaFailed("AceAppInstallStep.failed.missingModule", device.getName(), missingModules.encodeToString());
          details.complete(BJobState.failed);
          return;
        }

        // Otherwise return generic failure message
        details.vaFailed("AceAppInstallStep.failed.installFailed", device.getName());
        details.complete(BJobState.failed);
        return;
      }

      // We got here, job was successful
      details.success();
      details.complete(BJobState.success);

    }
    catch (Exception e)
    {
      details.failed("provisioningNiagara", "AceAppInstallStep.failed.exception", device.getName(), e);
      details.complete(BJobState.failed);
    }
    return;
  }

  // This code is separated into a method to make testing easier
  public BObject getTargetObject(BFoxSession foxSession)
  {
    // Use the Fox session as the root for resolving station ORDs
    BOrd targetRootOrd = BOrd.make("station:|slot:/Drivers");
    BComponent targetRoot = (BComponent)targetRootOrd.get(foxSession);
    targetRoot.lease(2);
    BComponent[] aceDeviceArray = CompUtil.getDescendants(targetRoot, BAceDevice.class);
    return Arrays.asList(aceDeviceArray).stream().findAny().get();
  }

  // Poll to check if the BAceAppDownloadJob is complete
  public void waitForJobComplete(BAceAppDownloadJob job)
  {
    int count = 0;
    while(!job.getJobState().isComplete() && count<240)
    {
      DrUtil.wait(500);
      count++;
    }
  }

  @Override
  public String toString(Context cx)
  {
    return lexicon.getText("AceAppInstallStep.display", getAceFileName());
  }

  public static final Lexicon lexicon = Lexicon.make("aceAppProv");
  public static final String ACE_CACHE = "file:^aceFileCache";
}
