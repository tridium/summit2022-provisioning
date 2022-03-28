/*
 * Copyright 2022 Tridium, Inc. All Rights Reserved.
 */

package com.acme.aceAppProv.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.baja.batchJob.BBatchJobService;
import javax.baja.batchJob.BJobStepDetails;
import javax.baja.batchJob.BValueList;
import javax.baja.batchJob.driver.BDeviceJobStep;
import javax.baja.batchJob.driver.BDeviceStepDetails;
import javax.baja.batchJob.driver.BNetworkBatchAgent;
import javax.baja.batchJob.driver.DeviceNetworkJobOp;
import javax.baja.file.BFileSystem;
import javax.baja.file.BIFile;
import javax.baja.file.FilePath;
import javax.baja.job.BJobState;
import javax.baja.naming.BLocalHost;
import javax.baja.naming.BOrd;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.nre.util.ByteBuffer;
import javax.baja.nre.util.FileUtil;
import javax.baja.provisioningNiagara.BForEachStationStage;
import javax.baja.provisioningNiagara.BNiagaraNetworkJob;
import javax.baja.security.BPassword;
import javax.baja.security.BPasswordAuthenticator;
import javax.baja.security.BUsernameAndPassword;
import javax.baja.sys.BComponent;
import javax.baja.sys.BSimple;
import javax.baja.sys.BStation;
import javax.baja.sys.BString;
import javax.baja.sys.Context;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.user.BUser;
import javax.baja.xml.XWriter;

import com.acme.aceAppProv.BAceAppInstallStep;
import org.mockito.*;
import org.mockito.stubbing.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.tridium.ace.BAceDevice;
import com.tridium.ace.BAceIpcNetwork;
import com.tridium.ace.kit.ACE;
import com.tridium.ace.kit.KitRegistry;
import com.tridium.ace.program.BAceAppDownloadJob;
import com.tridium.ace.sys.BAceApp;
import com.tridium.fox.sys.BFoxClientConnection;
import com.tridium.nd.BNiagaraNetwork;
import com.tridium.nd.BNiagaraStation;
import com.tridium.platform.daemon.BDaemonSession;
import com.tridium.platform.daemon.BHostProperties;
import com.tridium.platform.daemon.message.AuthenticationInfoMessage;
import com.tridium.platform.daemon.message.DaemonMessage;
import com.tridium.platform.daemon.message.GetAppListMessage;
import com.tridium.platform.daemon.message.SystemPasswordMessage;
import com.tridium.provisioningNiagara.BProvisioningNiagaraNetworkExt;
import com.tridium.provisioningNiagara.ProvisioningConnectionUtil;
import com.tridium.testng.BStationTestBase;

@NiagaraType
public class BAceAppInstallStepTest
  extends BStationTestBase
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.acme.aceAppProv.test.BAceAppInstallStepTest(2979906276)1.0$ @*/
/* Generated Fri Feb 18 12:14:28 EST 2022 by Slot-o-Matic (c) Tridium, Inc. 2012 */

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BAceAppInstallStepTest.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/


  @Override
  @BeforeTest(alwaysRun=true,description = "Setup and start test station")
  public void setupStation() throws Exception
  {
    super.setupStation();
    // Set up the supervisor station user
    provisioningUser = new BUser();
    provisioningUser.setRoles(TEST_SUPER_USER);
    BPasswordAuthenticator authenticator = new BPasswordAuthenticator();
    authenticator.setPassword(BPassword.make("Password10"));
    provisioningUser.setAuthenticator(authenticator);
    getUserService().add("provisioning", provisioningUser);

    // Add a provisioning network extension
    BStation station = stationHandler.getStation();
    BNiagaraNetwork niagaraNetwork = (BNiagaraNetwork) station.get("Drivers").asComponent().get("NiagaraNetwork");
    niagaraNetwork.add("ProvisioningNwExt", new BProvisioningNiagaraNetworkExt());
    BBatchJobService batchService = new BBatchJobService();
    station.getServices().add("BatchJobService", batchService);

    // Initialize another station as the provisioning target
    targetStation = new BNiagaraStation();
    targetStation.setAddress(BOrd.make("ip:localhost"));
    BFoxClientConnection foxConnection = targetStation.getClientConnection();
    foxConnection.setPort(foxPort);
    foxConnection.setUseFoxs(false);
    foxConnection.setCredentials(new BUsernameAndPassword("provisioning", "Password10"));

    // Configure a test platform connection for the target station
    // This test set does not utilize the platform connection, but it is included here as an example
    BTestPlatformConnection connection = new BTestPlatformConnection();
    BDaemonSession session = makeDaemonSession(false, false, true);
    connection.setDaemonSession(session);
    targetStation.add("provisioningNiagara_PlatformConnection", connection);

    niagaraNetwork.add(station.getStationName(), targetStation);

    // BHistoryJobSummaryManager creates the necessary histories at startup asynchronously.
    // This shouldn't be an issue in normal circumstances, but in the tests where we run
    // batch jobs immediately after startup, they may finish before the histories are created,
    // causing a NullPointerException. Wait until this process completes.
    for (int i = 0; i < 20 && !batchService.whenServiceStarted().isDone(); i++)
    {
      Thread.sleep(100);
    }
    if (!batchService.whenServiceStarted().isDone())
    {
      throw new SkipException("Timed out waiting for batch service to initialize.");
    }

    // Copy the test file into the expected supervisor station home location
    BOrd aceFileOrd = BOrd.make("module://aceAppProvTest/rc/" + rcFileName);
    BIFile aceFile = (BIFile)aceFileOrd.resolve().get();
    FilePath acePath = new FilePath("^aceFileCache").merge("app.ace");
    try
    {
      aceToInstall = BFileSystem.INSTANCE.makeFile(acePath);

      try (
        InputStream templateIn = aceFile.getInputStream();
        OutputStream templateOut = aceToInstall.getOutputStream();
      )
      {
        FileUtil.pipe(templateIn, templateOut);
      }
      catch (IOException ioe)
      {
        LOGGER.log(Level.WARNING, "Unable to copy ACE file", ioe);
        Assert.fail("Unable to copy ACE file", ioe);
        throw ioe;
      }
    }
    catch (Exception e)
    {
      LOGGER.log(Level.WARNING, "Unable to import ACE file", e);
      Assert.fail("Unable to import ACE file", e);
    }

    // Check that the file is there
    Assert.assertNotNull(aceToInstall, "ACE file does not exist");

  }

  /**
   * Reset the connection util Fox session before each test method
   *
   * @throws Exception
   */
  @BeforeMethod
  public void beforeMethod() throws Exception
  {
    station = stationHandler.getStation();
    try (ProvisioningConnectionUtil util = new ProvisioningConnectionUtil(targetStation, null))
    {
      util.getEngagedFoxSession().disconnect();
    }
  }

  /**
   * Run a job step and return the results
   *
   * @param step the job step to run
   * @return the results
   */
  private BJobStepDetails runStep(BDeviceJobStep step)
  {
    BNiagaraNetworkJob job = new BNiagaraNetworkJob();
    BSimple deviceId = BNetworkBatchAgent.get(getNiagaraNetwork(), null).getBatchDeviceId(targetStation);
    job.setDevicesToProcess(BValueList.make(deviceId));
    BForEachStationStage stage = job.getForEachStationStage();
    stage.addStep(step);

    BStation station = stationHandler.getStation();
    station.add("job", job);
    try
    {
      BBatchJobService batchService = (BBatchJobService) Sys.getService(BBatchJobService.TYPE);
      BDeviceStepDetails details = step.run(batchService, targetStation, (DeviceNetworkJobOp) job.makeOp(null));
      for (int i = 0; i < job.log().getItems().length; ++i)
      {
        LOGGER.info("  JobLogItem[" + i + "] = \"" + job.log().getItems()[i] + "\"");
      }

      return details;
    }
    finally
    {
      station.remove("job");
    }
  }

  /**
   * Make a mock daemon session that will respond to requests based on the specified parameters
   *
   * @param defaultCredentials true if the daemon session should return default credentials with auth servlet requests
   * @param defaultSysPw true if the daemon session should return default system passphrase with systempw requests
   * @param stationRunning true if the daemon session should return a running station with applist requests
   * @return the daemon session
   * @throws IOException if an error occurs writing XML responses
   */
  private static BDaemonSession makeDaemonSession(boolean defaultCredentials, boolean defaultSysPw, boolean stationRunning) throws IOException
  {
    // The Mockito.spy() method will only mock specific methods we configure, otherwise the actual methods are called in the session here.
    BDaemonSession session = Mockito.spy(BDaemonSession.makeIgnoringCache(BLocalHost.INSTANCE, 3011));
    BHostProperties hostProperties = session.getHostProperties();
    hostProperties.setDaemonVersion("4.13");
    hostProperties.setSslSupported(true);
    hostProperties.getServletNames().add("systempw", BString.make("systempw"));
    hostProperties.getServletNames().add("applist", BString.make("applist"));

    // Configure platform message contents for specific platform daemon message types
    ByteBuffer authBuffer = new ByteBuffer();
    XWriter auth = new XWriter(authBuffer.getOutputStream());
    auth.w("<authInfo>");
    auth.w("<auth>");
    auth.w("<user").w(' ').attr("name", "foo");
    if (defaultCredentials)
    {
      auth.w(' ').attr("default", "true");
    }
    auth.w("/>");
    auth.w("</auth>");
    auth.w("</authInfo>");
    auth.flush();
    auth.close();
    byte[] authBytes = authBuffer.getBytes();

    ByteBuffer syspwBuffer = new ByteBuffer();
    XWriter syspw = new XWriter(syspwBuffer.getOutputStream());
    syspw.w("<systemPassword");
    if (defaultSysPw)
    {
      syspw.w(' ').attr("default", "true");
    }
    syspw.w("/>");
    syspw.flush();
    syspw.close();
    byte[] syspwBytes = syspwBuffer.getBytes();

    ByteBuffer applistBuffer = new ByteBuffer();
    XWriter applist = new XWriter(applistBuffer.getOutputStream());
    applist.w("<apps>\n");
    applist.w("<app ").attr("type", "station").w(">\n");
    applist.w("<station ").attr("name", "station").w(">\n");
    String status = String.valueOf(stationRunning ? APP_STATUS_RUNNING : APP_STATUS_IDLE);
    applist.w("<status ").attr("value", status).w("/>\n");
    applist.w("</station>");
    applist.w("</app>\n");
    applist.w("</apps>\n");
    applist.flush();
    applist.close();
    byte[] applistBytes = applistBuffer.getBytes();

    // Answer lambda returns mocked messages for specific platform daemon message types
    Answer<InputStream> answer = invocation -> {
      DaemonMessage message = invocation.getArgument(0);
      if (message instanceof AuthenticationInfoMessage)
      {
        return new ByteArrayInputStream(authBytes);
      }
      else if (message instanceof SystemPasswordMessage)
      {
        return new ByteArrayInputStream(syspwBytes);
      }
      else if (message instanceof GetAppListMessage)
      {
        return new ByteArrayInputStream(applistBytes);
      }
      return null;
    };

    // Configure the session getInputStream() methods to respond with the mocked Answer result
    Mockito.doAnswer(answer).when(session).getInputStream(ArgumentMatchers.any(DaemonMessage.class), ArgumentMatchers.anyString());
    Mockito.doAnswer(answer).when(session).getInputStream(ArgumentMatchers.any(DaemonMessage.class));

    return  session;
  }


  @Test
  public void testAceAppInstallToEmptyStation() throws Exception
  {
    BAceAppInstallStep step = new BAceAppInstallStep();
    step.setAceFileName(aceToInstall.getFileName());
    BJobStepDetails details = runStep(step);

    String jobMessages = Arrays.asList(
        details.log().getItems())
      .stream()
      .map( item -> { return item.getMessage(); } )
      .reduce("\n", (partialString, element) -> partialString + element + '\n');

    Assert.assertEquals(details.getState(), BJobState.failed,
      String.format("Job failed: %s", jobMessages));

    Assert.assertTrue(jobMessages.contains("The necessary ACE device was not found on target device"));
  }

  @Test(dependsOnMethods = "testAceAppInstallToEmptyStation")  // We want to run the empty station test first
  public void testAceAppInstall() throws Exception
  {
    // Add ACE Network and Device to target station
    BComponent stationRoot = targetStation.getComponentSpace().getRootComponent();
    BComponent drivers = stationRoot.get("Drivers").asComponent();
    drivers.add("AceEdgeNetwork", aceNetwork);
    BAceDevice aceDevice = aceNetwork.getLocal();
    aceDevice.add("App", new BAceApp());

    // Set up some Mocking for unit testing the install step
    BAceAppInstallStep step = Mockito.spy(new BAceAppInstallStep());
    Mockito.when(step.getTargetObject(ArgumentMatchers.any())).thenReturn(aceDevice);
    Mockito.doAnswer( invocation ->
      {
        BAceAppDownloadJob job = invocation.getArgument(0);
        job.complete(BJobState.success);
        return null;
      }
    ).when(step).waitForJobComplete(ArgumentMatchers.any(BAceAppDownloadJob.class));

    // Run the step
    step.setAceFileName(aceToInstall.getFileName());
    BJobStepDetails details = runStep(step);

    String jobMessages = Arrays.asList(
        details.log().getItems())
      .stream()
      .map( item -> { return item.getMessage(); } )
      .reduce("\n", (partialString, element) -> partialString + element + '\n');

    Assert.assertEquals(details.getState(), BJobState.success,
      String.format("Job failed: %s", jobMessages));

    Assert.assertTrue(jobMessages.contains("ACE application installation job"));
  }


  // Override methods in BAceIpcNetwork for testing
  private final BAceIpcNetwork aceNetwork = new BAceIpcNetwork()
  {
    @Override
    public void aceNetworkStarted()
      throws Exception
    {
    }

    @Override
    public void stopped()
      throws Exception
    {
    }

    @Override
    public void changed(Property p, Context cx)
    {
    }

    @Override
    public KitRegistry getNetworkKitRegistry()
    {
      return (KitRegistry) ACE.getKitRegistry("module://aceAppProvTest/rc/catalog_test.json");
    }
  };

  private static final String rcFileName = "app.ace";
  private BIFile aceToInstall = null;

  private static final Logger LOGGER = Logger.getLogger("ProvisioningJobStepTestBase");

  // These were pulled from the platform niagarad App class so we won't have to add that dependency
  public static final int APP_STATUS_IDLE = 0;
  public static final int APP_STATUS_STARTING = 1;
  public static final int APP_STATUS_RUNNING = 2;
  public static final int APP_STATUS_STOPPING = 3;
  public static final int APP_STATUS_FAILED = 4;
  public static final int APP_STATUS_UNKNOWN = 5;
  public static final int APP_STATUS_HALTED = 6;

  protected BUser provisioningUser;
  protected BNiagaraStation targetStation;
}