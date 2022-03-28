/*
 * Copyright 2022 Tridium, Inc. All Rights Reserved.
 */

package com.acme.aceAppProv.test;

import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import com.tridium.platform.daemon.BDaemonSession;
import com.tridium.provisioningNiagara.BPlatformConnection;

@NiagaraType
public class BTestPlatformConnection
  extends BPlatformConnection
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.edgeex.aceAppProv.test.BTestPlatformConnection(2979906276)1.0$ @*/
/* Generated Wed Feb 12 15:29:34 EST 2020 by Slot-o-Matic (c) Tridium, Inc. 2012 */

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BTestPlatformConnection.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  @Override
  public BDaemonSession getDaemonSession()
  {
    return session;
  }

  public void setDaemonSession(BDaemonSession session)
  {
    this.session = session;
  }

  private BDaemonSession session;
}
