package lanball;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.stage.*;
import javafx.event.Event;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class UiChecks extends Application {
    static LanBall app;static Stage window;static Path output;static int checks;
    static <T>T fx(Callable<T> task)throws Exception{FutureTask<T> future=new FutureTask<>(task);Platform.runLater(future);return future.get(10,TimeUnit.SECONDS);}
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS UI "+(++checks)+" "+text);}
    static void await(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+10_000_000_000L;
        while(!fx(condition)){if(System.nanoTime()>end)throw new AssertionError("UI timed out");Thread.sleep(30);}}
    static void closeDialogs()throws Exception{fx(()->{for(Window other:List.copyOf(Window.getWindows()))if(other!=window)other.hide();return null;});}
    static void shot(String name,Window selected)throws Exception{fx(()->{
        Scene scene=selected.getScene();scene.getRoot().applyCss();scene.getRoot().layout();WritableImage image=scene.snapshot(null);
        int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];
        image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
        BufferedImage png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);
        ImageIO.write(png,"png",output.resolve(name+".png").toFile());return null;});}
    static Button button(Parent root,String caption){for(Node node:root.getChildrenUnmodifiable()){
        if(node instanceof Button b&&b.getText().equals(caption))return b;
        if(node instanceof Parent p){Button b=button(p,caption);if(b!=null)return b;}}return null;}
    static void touch(Button button,int id,boolean down){var point=new TouchPoint(id,down?TouchPoint.State.PRESSED:TouchPoint.State.RELEASED,
        20,20,20,20,button,new PickResult(button,20,20));Event.fireEvent(button,new TouchEvent(down?TouchEvent.TOUCH_PRESSED:TouchEvent.TOUCH_RELEASED,
                point,List.of(point),id,false,false,false,false));}
    public void start(Stage stage)throws Exception{
        output=Path.of(getParameters().getRaw().get(0));Files.createDirectories(output);
        System.setProperty("user.home",Files.createTempDirectory(output,"prefs-").toAbsolutePath().toString());
        app=new LanBall();window=stage;stage.setX(-2400);app.start(stage);
        new Thread(()->{try{
            check(fx(()->app.arena.sounds.size()==4),"four bundled sound effects load");
            shot("01-home",window);
            fx(()->{app.name.setText("Asha");app.pin.setText("123456");app.host.fire();return null;});
            await(()->app.arena.latest!=null&&app.arena.latest.players().containsKey("Asha"));
            closeDialogs();check(fx(()->!app.setup.isExpanded()),"secure host connection opens lobby");
            Server server=fx(()->app.server);
            try(Checks.Client peer=new Checks.Client(Wire.PORT,server.fingerprint,"Binu","654321",true)){
                peer.send("T\t1");peer.send("Y\t1");
                fx(()->{app.team.getSelectionModel().select(0);return null;});
                await(()->server.players.get("Asha").team==0);
                fx(()->{app.ready.fire();return null;});await(()->server.players.get("Asha").ready);
                fx(()->{app.team.getSelectionModel().select(2);return null;});
                Thread.sleep(150);check(!server.players.get("Asha").ready,"UI team change leaves server unready");
                fx(()->{app.team.getSelectionModel().select(0);return null;});await(()->server.players.get("Asha").team==0);
                fx(()->{app.ready.fire();return null;});await(()->server.players.get("Asha").ready);
                fx(()->{app.server.startRound();return null;});await(()->app.arena.latest.header()[1].equals("Playing"));
                check(fx(()->app.team.isDisabled()),"roster controls lock while playing");
                fx(()->{
                    Button right=button(stage.getScene().getRoot(),"▶"),kick=button(stage.getScene().getRoot(),"KICK");
                    touch(right,1,true);touch(kick,2,true);check((app.input&18)==18,"simultaneous touch movement and kick");
                    touch(kick,2,false);check((app.input&18)==2,"releasing kick preserves movement");
                    touch(right,1,false);check(app.input==0,"touch release clears movement");
                    app.keys.add(KeyCode.W);app.keys.add(KeyCode.UP);app.readInput();app.keys.remove(KeyCode.W);app.readInput();
                    check((app.input&4)!=0,"alternate key stays active");app.release();check(app.input==0,"focus release clears input");
                    return null;
                });
                shot("02-stadium-desktop",window);
                fx(()->{stage.setWidth(390);stage.setHeight(844);return null;});Thread.sleep(300);
                shot("03-stadium-phone",window);check(fx(()->app.arena.getWidth()<360),"pitch fits phone width");
                fx(()->{stage.setWidth(1100);stage.setHeight(920);Panels.settings(app);return null;});Thread.sleep(200);
                Window settings=fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow());
                shot("04-settings",settings);closeDialogs();
                fx(()->{app.server.finish(true);return null;});await(()->server.phase.equals("Result"));
                await(()->Window.getWindows().size()>1);
                Window report=fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow());
                shot("05-league",report);closeDialogs();
                check(fx(()->!app.ready.isSelected()),"result clears client ready");
                fx(()->{Panels.response(app,"D\thistory\n"+server.league.history(""));return null;});Thread.sleep(200);
                Window history=fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow());
                shot("06-history",history);closeDialogs();
                fx(()->{Panels.configure(app);return null;});Thread.sleep(150);
                shot("07-match-lab",fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow()));closeDialogs();
                fx(()->{Panels.tournament(app);return null;});Thread.sleep(150);
                shot("08-tournament",fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow()));closeDialogs();
                String replay=server.league.replay(server.id);fx(()->{Panels.replay(app,replay);return null;});Thread.sleep(150);
                shot("09-replay",fx(()->Window.getWindows().stream().filter(w->w!=window).findFirst().orElseThrow()));closeDialogs();
                check(true,"league, history, match lab, tournament and replay screens render");
                fx(()->{
                    String[] current={"P","Asha","0","120","100"},old={"P","Asha","0","100","100"};
                    check(app.arena.position(current,old,3,.5)==110,"interpolation produces intermediate positions");
                    current[3]="400";check(app.arena.position(current,old,3,.5)==400,"teleports are not interpolated");return null;
                });
            }
            fx(()->{app.disconnect();return null;});check(fx(()->app.server==null&&app.setup.isExpanded()),"leave resets interface");
            fx(()->{app.stop();stage.close();Platform.exit();return null;});
            System.out.println("ALL "+checks+" UI CHECKS PASSED");System.exit(0);
        }catch(Throwable error){error.printStackTrace();try{fx(()->{app.stop();return null;});}catch(Exception ignored){}System.exit(1);}},"ui-tests").start();
    }
    public static void main(String[] args){launch(args);}
}
