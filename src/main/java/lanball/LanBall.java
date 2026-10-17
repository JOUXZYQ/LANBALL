package lanball;
import javafx.application.*;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLSocket;

public final class LanBall extends Application {
    final TextField address=new TextField("127.0.0.1"), name=new TextField(), club=new TextField("Campus");
    final TextField shirt=new TextField("7"), fingerprint=new TextField(), chat=new TextField();
    final PasswordField pin=new PasswordField();
    final CheckBox create=new CheckBox("Create profile"), ready=new CheckBox("READY");
    final ComboBox<String> team=new ComboBox<>();
    final Label score=new Label("LANBALL / 04"), status=new Label("A small pitch. A proper rivalry."), roster=new Label();
    final Label connection=new Label("Offline"), messages=new Label();
    final Arena arena=new Arena();
    final Set<KeyCode> keys=new HashSet<>();
    final ArrayBlockingQueue<String> commands=new ArrayBlockingQueue<>(32);
    final Properties preferences=new Properties();
    final Path settings=Path.of(System.getProperty("user.home"),"LanBall","v41-settings.properties");
    final TitledPane setup=new TitledPane();
    final TitledPane navigation=new TitledPane();
    final Button host=new Button("HOST MATCH"), join=new Button("JOIN MATCH");
    volatile int input, generation;
    volatile Socket socket;
    volatile String newest="";
    Server server;
    Stage window;
    int touch,controlVersion;
    boolean syncing;
    String lastFrame="", lastPhase="";
    final Timeline updates=new Timeline(new KeyFrame(Duration.millis(16),event -> update()));
    public void start(Stage stage) {
        window=stage; stage.setTitle("LanBall 4.1 / After Hours");
        try { if(Files.exists(settings)) preferences.load(Files.newBufferedReader(settings)); } catch(IOException ignored) { }
        address.setText(preferences.getProperty("address","127.0.0.1")); name.setText(preferences.getProperty("name",""));
        fingerprint.setText(preferences.getProperty("fingerprint","")); create.setSelected(true);
        team.getItems().addAll("RED", "BLUE", "WATCH"); team.getSelectionModel().select(2);
        team.setOnAction(e -> { if(!syncing) { ready.setSelected(false); send("T\t"+team.getSelectionModel().getSelectedIndex()); } });
        ready.setOnAction(e -> send("Y\t"+(ready.isSelected()?1:0)));
        host.setOnAction(e -> connect(true,false)); join.setOnAction(e -> connect(false,false));
        fingerprint.setPrefColumnCount(30); fingerprint.setPromptText("Paste the SHA-256 fingerprint shown by your host");
        setup.setText("CONNECT / PROFILE"); setup.setContent(new VBox(10,
                flow(field("Host IP",address),field("Player",name),field("PIN",pin),field("Club",club),field("Shirt",shirt)),
                field("Verified host fingerprint",fingerprint),flow(create,host,join,button("Find hosts",this::discover))));
        FlowPane heading=flow(score,connection);connection.setMinWidth(140);
        score.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        score.maxWidthProperty().bind(stage.widthProperty().subtract(60));chat.setStyle("-fx-prompt-text-fill: #a9bfd0;");
        FlowPane tools=flow(button("Practice",()->connect(true,true)),button("Match setup",()->Panels.configure(this)),
                button("Balance",()->{if(server!=null)server.balance();}),button("League",this::league),
                button("History",()->send("Q\thistory\t")),button("Tournament",()->Panels.tournament(this)),
                button("Settings",()->Panels.settings(this)),button("Help",()->notice(Panels.HELP)));
        FlowPane lobby=flow(team,ready,button("START / REMATCH",()->{if(server!=null)notice(server.startRound());}),
                button("Abandon",()->{if(server!=null && confirm("End this match as abandoned?"))server.finish(false);}),
                button("Leave",()->{if(server==null || confirm("Close your host and end the session?"))disconnect();}));
        GridPane pad=new GridPane(); pad.setHgap(6); pad.setVgap(6);
        pad.add(direction("▲",4),1,0);pad.add(direction("◀",1),0,1);pad.add(direction("▼",8),1,1);pad.add(direction("▶",2),2,1);
        HBox controls=new HBox(24,pad,direction("KICK",16)); controls.setAlignment(Pos.CENTER);
        chat.setPromptText("Team spirit starts here. Say hello..."); chat.setOnAction(e->{send("C\t"+Wire.clean(chat.getText(),120));chat.clear();});
        navigation.setText("CLUBHOUSE / match • league • settings");navigation.setContent(new VBox(10,tools,setup,lobby));
        VBox content=new VBox(12,heading,navigation,status,arena,roster,controls,chat,messages);
        content.setPadding(new Insets(22)); content.setMinWidth(0);
        for(Label label:List.of(status,score,roster,connection,messages))label.setWrapText(true);
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);
        arena.widthProperty().bind(content.widthProperty().subtract(44));
        arena.heightProperty().bind(javafx.beans.binding.Bindings.min(480,arena.widthProperty().multiply(.59)));
        Scene scene=new Scene(scroll,1100,920);
        scene.addEventFilter(KeyEvent.KEY_PRESSED,e->key(e,true));scene.addEventFilter(KeyEvent.KEY_RELEASED,e->key(e,false));
        stage.focusedProperty().addListener((o,a,b)->{if(!b)release();});stage.setScene(scene);theme();stage.show();
        updates.setCycleCount(Timeline.INDEFINITE);updates.play();
    }
    static FlowPane flow(Node... nodes){return new FlowPane(10,10,nodes);}
    static Button button(String text,Runnable action){Button b=new Button(text);b.setOnAction(e->action.run());return b;}
    static VBox field(String label,TextField input){input.setPrefColumnCount(10);input.setAccessibleText(label);return new VBox(4,new Label(label),input);}
    void theme(){
        window.getScene().getRoot().setStyle("-fx-base: #111f31; -fx-background: #08111e; -fx-control-inner-background: #102034;"
                +"-fx-accent: #40e0b5; -fx-focus-color: #40e0b5; -fx-text-fill: #eef6ff; -fx-font-family: 'Segoe UI';"
                +"-fx-font-size: "+preferences.getProperty("font","14")+"px;");
        arena.muted=Boolean.parseBoolean(preferences.getProperty("mute","false"));
        arena.reducedMotion=Boolean.parseBoolean(preferences.getProperty("motion","false"));
        arena.smoothing=Boolean.parseBoolean(preferences.getProperty("smooth","true"));
        arena.volume=Double.parseDouble(preferences.getProperty("volume","0.35"));
        if(Boolean.parseBoolean(preferences.getProperty("contrast","false")))window.getScene().getRoot().setStyle(
                "-fx-base: black; -fx-background: black; -fx-control-inner-background: black; -fx-text-fill: white; -fx-font-size: 18px;");
    }
    Button direction(String text,int bit){
        Button b=new Button(text);b.setMinSize(64,56);b.setFocusTraversable(false);
        b.setOnTouchPressed(e->{e.getTouchPoint().grab(b);touch|=bit;readInput();e.consume();});
        b.setOnTouchReleased(e->{touch&=~bit;readInput();e.consume();});
        b.setOnMousePressed(e->{if(!e.isSynthesized()){touch|=bit;readInput();}});
        b.setOnMouseReleased(e->{if(!e.isSynthesized()){touch&=~bit;readInput();}});return b;
    }
    void key(KeyEvent event,boolean down){
        if(down && event.getTarget() instanceof TextInputControl)return;
        if(down)keys.add(event.getCode());else keys.remove(event.getCode());readInput();
        if(input!=0)event.consume();
        if(event.getCode()==KeyCode.ESCAPE){release();navigation.setExpanded(!navigation.isExpanded());}
        if(event.getCode()==KeyCode.ENTER && down){release();chat.requestFocus();}
    }
    void readInput(){
        int value=touch;String[] defaults={"A","D","W","S","SPACE"},arrows={"LEFT","RIGHT","UP","DOWN","X"};
        for(int i=0;i<5;i++)if(keys.contains(KeyCode.valueOf(preferences.getProperty("key"+i,defaults[i])))
                ||keys.contains(KeyCode.valueOf(arrows[i])))value|=1<<i;input=value;
    }
    void release(){keys.clear();touch=0;input=0;}
    void send(String value){if(socket==null)return;boolean control=value.startsWith("T\t")||value.startsWith("Y\t");
        if(control)value+="\t"+(++controlVersion);
        if(!commands.offer(value)){if(control)controlVersion--;notice("Please wait for the network.");}}
    void connect(boolean hosting,boolean practice){
        if(practice){name.setText("Practice");pin.setText("123456");create.setSelected(true);}
        disconnect();int owner=generation;String destination=hosting?"127.0.0.1":address.getText().strip();
        String hello=String.join("\t","LB41",name.getText(),pin.getText(),club.getText(),shirt.getText(),create.isSelected()?"create":"login");
        String expected=fingerprint.getText().strip();host.setDisable(true);join.setDisable(true);status.setText("Connecting securely...");
        Wire.background(()->{
            try{
                if(hosting){
                    Path folder=practice?Files.createTempDirectory("lanball-practice"):Path.of("lanball-v41-data");
                    Server created=new Server(folder,Wire.PORT);created.practice=practice;
                    synchronized(this){if(owner!=generation){created.close();return;}server=created;}
                }
                String pinToHost=hosting?server.fingerprint:expected;int attempts=0;
                while(owner==generation){
                    try(SSLSocket connected=Wire.client(pinToHost)){
                        synchronized(this){if(owner!=generation)return;socket=connected;}
                        connected.connect(new InetSocketAddress(destination,Wire.PORT),5000);
                        connected.setSoTimeout(8000);connected.setTcpNoDelay(true);connected.startHandshake();
                        var in=new DataInputStream(connected.getInputStream());var out=new DataOutputStream(connected.getOutputStream());
                        Wire.send(out,hello);String answer=Wire.read(in,2048);if(!answer.equals("OK"))throw new IOException(answer);
                        Platform.runLater(()->{if(owner!=generation)return;setup.setExpanded(false);fingerprint.setText(pinToHost);
                            connection.setText("TLS • "+destination);status.setText("Connected. Choose your team.");saveSettings();});
                        String localAddresses=hosting?addresses():destination;
                        if(hosting)Platform.runLater(()->{if(owner==generation)notice("Host IP: "+localAddresses+"\nFingerprint:\n"+pinToHost
                                +"\n"+String.join("\n",server.league.problems));});
                        Wire.background(()->{try{while(owner==generation&&!connected.isClosed()){
                            String command=commands.poll();if(command!=null)Wire.send(out,command);
                            Wire.send(out,"I\t"+input);Thread.sleep(16);
                        }}catch(Exception error){try{connected.close();}catch(IOException ignored){}}});
                        while(owner==generation){String data=Wire.read(in,8_000_000);
                            if(data.startsWith("E\t"))Platform.runLater(()->{if(owner==generation)notice(data.substring(2));});
                            else if(data.startsWith("D\t"))Platform.runLater(()->{if(owner==generation)Panels.response(this,data);});
                            else if(owner==generation)newest=data;}
                    }catch(IOException error){
                        if(owner!=generation)return;
                        if(++attempts>3||error.getMessage().startsWith("E\t")||error instanceof javax.net.ssl.SSLException)throw error;
                        Platform.runLater(()->connection.setText("Reconnecting..."));Thread.sleep(1000);
                    }
                }
            }catch(Exception error){Platform.runLater(()->{if(owner==generation){disconnect();notice("Connection: "+error.getMessage());}});}
        });
    }
    void update(){
        String value=newest;if(value.isEmpty()||value.equals(lastFrame))return;lastFrame=value;arena.accept(value);
        if(arena.latest==null)return;String[] h=arena.latest.header();
        score.setText("RED  "+h[3]+"  :  "+h[4]+"  BLUE    /    "+h[2]+"s");
        StringBuilder list=new StringBuilder(),conversation=new StringBuilder();
        for(String line:value.split("\n")){String[] p=line.split("\t",-1);
            if(p[0].equals("M")&&!p[1].isBlank())status.setText(p[1]);
            if(p[0].equals("C"))conversation.append(p[1]).append('\n');
            if(p[0].equals("P")){list.append(p[1]).append(" · ").append(p[8]).append(p[5].equals("true")?" ✓   ":"   ");
                if(p[1].equalsIgnoreCase(name.getText())){if(Integer.parseInt(p[9])<controlVersion)continue;
                    syncing=true;team.getSelectionModel().select(Integer.parseInt(p[2]));
                    ready.setSelected(p[5].equals("true"));syncing=false;}}
        }
        roster.setText(list.toString());messages.setText(conversation.toString());
        boolean active=!h[1].equals("Lobby")&&!h[1].equals("Result");team.setDisable(active);ready.setDisable(active);
        if(!lastPhase.equals(h[1])){if(h[1].equals("Playing"))navigation.setExpanded(false);
            if(h[1].equals("Result")){navigation.setExpanded(true);send("Q\tleague\t\t");}}lastPhase=h[1];
    }
    void saveSettings(){
        preferences.setProperty("address",address.getText());preferences.setProperty("name",name.getText());
        preferences.setProperty("fingerprint",fingerprint.getText());
        try{Files.createDirectories(settings.getParent());StringWriter out=new StringWriter();preferences.store(out,"LanBall preferences");
            League.atomic(settings,out.toString());}catch(IOException error){status.setText("Settings could not be saved.");}
    }
    void league(){
        if(socket!=null){send("Q\tleague\t\t");return;}
        try{notice("Offline: last cached host report.");Panels.response(this,Files.readString(settings.resolveSibling("league-cache.txt")));}
        catch(IOException error){notice("Connect to a host to load its league first.");}
    }
    void discover(){Wire.background(()->{try(DatagramSocket udp=new DatagramSocket()){
        udp.setBroadcast(true);udp.setSoTimeout(1500);byte[] data=Wire.bytes("LB41");
        udp.send(new DatagramPacket(data,data.length,InetAddress.getByName("255.255.255.255"),Wire.PORT+1));
        DatagramPacket reply=new DatagramPacket(new byte[512],512);udp.receive(reply);
        Platform.runLater(()->{address.setText(reply.getAddress().getHostAddress());notice("Found host. Verify its fingerprint before joining.");});
    }catch(Exception error){Platform.runLater(()->notice("No host found. Enter the host IP directly."));}});}
    static String addresses()throws SocketException{
        List<String> result=new ArrayList<>();for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))
            for(InetAddress ip:Collections.list(n.getInetAddresses()))if(ip instanceof Inet4Address&&!ip.isLoopbackAddress())result.add(ip.getHostAddress());
        return String.join(" / ",result);
    }
    void notice(String text){Alert alert=new Alert(Alert.AlertType.INFORMATION,text);alert.initOwner(window);alert.show();}
    boolean confirm(String text){return new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.YES,ButtonType.NO)
            .showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
    synchronized void disconnect(){
        generation++;Socket old=socket;Server previous=server;socket=null;server=null;
        Wire.background(()->{try{if(old!=null)old.close();}catch(IOException ignored){}if(previous!=null)previous.close();});
        release();commands.clear();controlVersion=0;newest=lastFrame="";arena.latest=arena.previous=null;arena.lastEvent=-1;
        host.setDisable(false);join.setDisable(false);team.setDisable(false);ready.setDisable(false);ready.setSelected(false);
        setup.setExpanded(true);navigation.setExpanded(true);connection.setText("Offline");
    }
    public void stop(){disconnect();updates.stop();arena.animation.stop();}
    public static void main(String[] args){launch(args);}
}
