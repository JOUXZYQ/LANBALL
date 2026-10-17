package lanball;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

public class Checks {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+(++checks)+" "+text);}
    static void await(java.util.function.BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+6_000_000_000L;
        while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("Timed out");Thread.sleep(10);}
    }
    static class Client implements AutoCloseable{
        final SSLSocket socket;
        final DataInputStream in;
        final DataOutputStream out;
        final AtomicInteger count=new AtomicInteger();
        final BlockingQueue<String> replies=new LinkedBlockingQueue<>();
        volatile String frame="";
        volatile int input;
        Client(int port,String fingerprint,String name,String pin,boolean create)throws Exception{
            socket=Wire.client(fingerprint);socket.connect(new InetSocketAddress("127.0.0.1",port));
            socket.setSoTimeout(3000);socket.setTcpNoDelay(true);socket.startHandshake();
            in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());
            Wire.send(out,String.join("\t","LB41",name,pin,"Campus","7",create?"create":"login"));
            String result=Wire.read(in,2000);if(!result.equals("OK")){socket.close();throw new IOException(result);}
            Wire.background(()->{try{while(!socket.isClosed()){Wire.send(out,"I\t"+input);Thread.sleep(16);}}catch(Exception ignored){}});
            Wire.background(()->{try{while(!socket.isClosed()){
                String data=Wire.read(in,8_000_000);if(data.startsWith("H\t")){frame=data;count.incrementAndGet();}else replies.add(data);
            }}catch(Exception ignored){}});
        }
        void send(String value)throws IOException{Wire.send(out,value);}
        public void close()throws IOException{socket.close();}
    }
    static void exercise(Server server,Client red,Client blue)throws Exception{
        server.clock.shutdownNow();Thread.sleep(50);
        red.send("T\t0");blue.send("T\t1");
        await(()->server.players.get("Asha").team==0&&server.players.get("Binu").team==1);
        check(server.phase.equals("Lobby"),"joining does not start a match");
        check(server.startRound().contains("ready"),"start requires explicit ready");
        red.send("Y\t1");await(()->server.players.get("Asha").ready);
        red.send("T\t2");Thread.sleep(150);
        check(!server.players.get("Asha").ready,"team change clears readiness despite continuous input");
        red.send("T\t0");red.send("Y\t1");blue.send("Y\t1");
        await(()->server.players.get("Asha").ready&&server.players.get("Binu").ready);
        server.startRound();check(server.phase.equals("Countdown"),"ready teams start with countdown");
        red.send("T\t2");Thread.sleep(50);check(server.players.get("Asha").team==0,"active roster is frozen");
        synchronized(server){
            Server.Player a=server.players.get("Asha"),b=server.players.get("Binu");
            a.x=b.x=480;a.y=b.y=280;server.collide();
            check(Math.hypot(a.x-b.x,a.y-b.y)>=31.99,"exactly overlapping bodies separate");
            a.x=470;b.x=490;a.y=b.y=280;a.vx=220;b.vx=-220;server.collide();
            check(a.x<b.x&&b.x-a.x>=31.99&&Math.abs(a.vx)<1,"head-on players block each other");
            a.x=20;b.x=30;a.y=b.y=100;server.collide();
            check(a.x>=16&&b.x-a.x>30,"wall collision resolution stays bounded");
            server.phase="Playing";server.deadline=0;server.ballX=480;server.ballY=450;
            a.x=200;a.y=200;a.input=2;a.lastInput=System.nanoTime();double before=a.x;
            server.move(System.nanoTime());check(a.x>before,"input moves authoritative body");
            a.lastInput=0;a.vx=0;before=a.x;server.move(System.nanoTime());check(a.x==before,"stale inputs are ignored");
            a.x=200;a.y=200;a.input=16;a.lastInput=System.nanoTime();server.ballX=228;server.ballY=200;
            server.ball(System.nanoTime());check(server.vx>500&&a.kicks==1,"kick has speed and statistic");
            server.ballX=228;server.ballY=200;server.ball(System.nanoTime());check(a.kicks==1,"kick cooldown enforced");
            a.input=b.input=0;a.lastInput=b.lastInput=0;server.ballX=965;server.ballY=280;server.vx=server.vy=0;
            server.ball(System.nanoTime());check(server.red==0,"partial crossing is not a goal");
            server.ballX=970;server.lastTouch="Asha";server.ball(System.nanoTime());
            check(server.red==1&&a.goals==1&&server.phase.equals("Goal"),"whole ball goal credits scorer and pauses");
            server.phase="Playing";server.ballX=970;server.ballY=280;server.lastTouch="Binu";server.ball(System.nanoTime());
            check(server.red==2&&b.own==1&&b.goals==0,"own goal is recorded separately");
            server.phase="Playing";server.ballX=1;server.ballY=(server.height-server.opening)/2;server.vx=-100;
            server.ball(System.nanoTime());check(server.vx>0,"solid goal post reflects ball");
            server.ranked=true;server.phase="Playing";b.connected=false;server.tick();
            check(server.phase.equals("Paused"),"ranked disconnect pauses round");
            b.connected=true;server.tick();check(server.phase.equals("Countdown"),"ranked reconnect restarts with kickoff");
            server.phase="Playing";server.finish(true);
        }
        await(()->server.phase.equals("Result"));
        check(server.league.rating("Asha")==1012&&server.league.rating("Binu")==988,"ranked Elo uses team means");
        check(!server.players.get("Asha").ready,"result clears readiness");
        check(server.league.report("","").startsWith("Player\tPlayed\tWon\tDrawn\tLost"),"league has named columns");
        red.send("Q\tleague\t\t");String report=red.replies.poll(3,TimeUnit.SECONDS);
        check(report!=null&&report.contains("Asha\t1\t1"),"host report downloads to client");
        red.send("Q\treplay\t"+server.id);String replay=red.replies.poll(3,TimeUnit.SECONDS);
        check(replay!=null&&replay.startsWith("D\treplay\n")&&replay.contains("H\t"),"saved replay downloads");
        red.send("Q\treplay\t../../bad");String error=red.replies.poll(3,TimeUnit.SECONDS);
        check(error!=null&&error.startsWith("E\t"),"invalid replay path reports an error");
        red.send("C\tHello team");Thread.sleep(50);server.tick();server.tick();Thread.sleep(70);
        check(red.frame.contains("Hello team"),"chat works after failed download");
        server.balance();check(!server.players.get("Asha").ready,"balance clears ready");
        check(server.configure(20,5,1,false,960,560,180).startsWith("Invalid"),"settings bounds validated");
        check(server.configure(180,5,1,false,Double.NaN,560,180).startsWith("Invalid"),"non-finite pitch rejected");
        server.configure(180,5,1,true,800,500,150);
        check(server.width==960&&server.height==560&&server.opening==180,"ranked enforces standard geometry");
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);Path data=Files.createTempDirectory(root,"league-");
        Files.writeString(data.resolve("bad.profile"),"broken");Files.writeString(data.resolve("bad.match"),"broken");
        Server server=new Server(data,0);
        try(server){
            check(server.league.problems.size()==2,"damaged profile and match do not block hosting");
            check(Files.readString(data.resolve("bad.match")).equals("broken"),"damaged files left unchanged");
            int port=server.listener.getLocalPort();
            try(SSLSocket wrong=Wire.client("0".repeat(64))){
                wrong.connect(new InetSocketAddress("127.0.0.1",port));
                try{wrong.startHandshake();throw new AssertionError("Bad pin accepted");}
                catch(SSLException expected){check(true,"certificate mismatch rejected before credentials");}
            }
            try(Client red=new Client(port,server.fingerprint,"Asha","123456",true);
                Client blue=new Client(port,server.fingerprint,"Binu","654321",true)){
                check(server.league.profiles.size()==2,"TLS clients register successfully");
                check(!Files.readString(data.resolve("player-asha.profile")).contains("123456"),"PIN not stored in plaintext");
                await(()->red.count.get()>3);int initial=red.count.get();long start=System.nanoTime();
                Thread.sleep(2100);double fps=(red.count.get()-initial)/((System.nanoTime()-start)/1e9);
                check(fps>=25,"snapshot cadence >=25/s; measured "+String.format(Locale.ROOT,"%.1f",fps));
                int tickStart=server.ticks;long began=System.nanoTime();
                List<CompletableFuture<Void>> logins=new ArrayList<>();
                for(int i=0;i<4;i++){int number=i;logins.add(CompletableFuture.runAsync(()->{
                    try(Client login=new Client(port,server.fingerprint,"Join"+number,"123456",true)){}
                    catch(Exception error){throw new CompletionException(error);}
                }));}CompletableFuture.allOf(logins.toArray(CompletableFuture[]::new)).get();
                double elapsed=(System.nanoTime()-began)/1e9;int advanced=server.ticks-tickStart;
                check(advanced>=Math.max(1,elapsed*45),"physics continues through concurrent password work: "+advanced+" ticks");
                try(Client duplicate=new Client(port,server.fingerprint,"Asha","123456",false)){
                    throw new AssertionError("Duplicate admitted");
                }catch(IOException expected){check(server.players.get("Asha").connected,"duplicate login cannot disconnect owner");}
                exercise(server,red,blue);
                String record=server.league.records.get(0);Path corrupt=data.resolve("late-corruption.match");
                Files.writeString(corrupt,record.replace("END","P\tBad\t0\tnot-a-number\nEND"));
                League reloaded=new League(data);
                check(reloaded.records.size()==1&&reloaded.rating("Asha")==1012,"damaged late row cannot partially alter totals");
                check(reloaded.profiles.size()==6,"profiles survive reload");
                server.league.setting("season","Finals");check(new League(data).settings.getProperty("season").equals("Finals"),"season persists");
                Path duplicate=data.resolve("duplicate.match");Files.writeString(duplicate,record);
                check(new League(data).records.size()==1,"duplicate match IDs cannot double-count standings");
                synchronized(server){
                    server.configure(30,5,1,false,960,560,180);
                    Server.Player a=server.players.get("Asha"),b=server.players.get("Binu");
                    a.team=0;b.team=1;a.ready=b.ready=true;
                    server.league.setting("cup.id","testcup");
                    server.league.setting("cup.rosters","Red:Asha\nBlue:Binu\nC:Join0\nD:Join1");
                    server.fixture="testcup:0-2";
                    check(server.startRound().contains("Wrong fixture"),"tournament enforces selected fixture rosters");
                    server.fixture="testcup:0-1";
                    check(server.startRound().contains("Get ready"),"valid tournament fixture starts");
                    Files.createDirectory(data.resolve(server.id+".match.pending"));
                    server.finish(true);
                }
                await(()->server.phase.equals("Save failed"));check(true,"write failure leaves recoverable pending result");
                Files.delete(data.resolve(server.id+".match.pending"));server.startRound();
                await(()->server.phase.equals("Result"));
                check(server.league.records.size()==2,"retry commits result once");
                synchronized(server){
                    server.players.get("Asha").ready=server.players.get("Binu").ready=true;
                    check(server.startRound().contains("already completed"),"completed fixture cannot be played twice");
                    server.fixture="";server.startRound();server.finish(false);
                }
                await(()->server.phase.equals("Result"));
                check(server.league.records.size()==3&&server.league.report("","").contains("Asha\t2\t1"),
                        "abandoned rounds are stored but excluded from standings");
                String finalReplay=server.league.replay(server.id);
                check(finalReplay.contains("H\tAbandoned"),"replay contains final match phase");
            }
        }
        check(server.listener.isClosed(),"shutdown closes host socket");
        System.out.println("ALL "+checks+" CHECKS PASSED. Data: "+data);
    }
}
