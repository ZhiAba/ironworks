package ironworks.unitmodule;

import arc.struct.Seq;

/** Exposes child sockets so other modules can be nested. */
public class SocketBehavior implements ModuleBehavior{
    public final Seq<ModuleSocket> sockets = new Seq<>();

    public SocketBehavior add(ModuleSocket socket){
        sockets.add(socket);
        return this;
    }

    @Override
    public void contributeSockets(Seq<ModuleSocket> out, ModuleContext ctx){
        out.addAll(sockets);
    }
}