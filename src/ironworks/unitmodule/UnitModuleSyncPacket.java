package ironworks.unitmodule;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.net.Packet;

/** Server-authoritative module graph sync for live changes. */
public class UnitModuleSyncPacket extends Packet{
    public int unitId;
    public UnitModuleState state = new UnitModuleState();

    public UnitModuleSyncPacket(){}

    public UnitModuleSyncPacket(int unitId, UnitModuleState state){
        this.unitId = unitId;
        this.state = state;
    }

    @Override
    public void write(Writes w){
        w.i(unitId);
        UnitModuleIO.write(w, state);
    }

    @Override
    public void read(Reads r){
        unitId = r.i();
        state = UnitModuleIO.read(r);
    }

    @Override
    public void handleClient(){
        UnitModuleRuntime.applyRemote(unitId, state);
    }

    @Override
    public int getPriority(){
        return Packet.priorityNormal;
    }
}