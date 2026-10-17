package lanball;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.*;

final class Panels {
    static final String HELP="Host on a computer; join on the same Wi-Fi using its IP and verified fingerprint.\n"
            +"Choose a team, mark ready, then the host starts. WASD/arrows move; Space/X kicks; Enter chats.\n"
            +"Red attacks right. Goals need the whole ball to cross. Ranked uses the standard pitch.\n"
            +"Ranked disconnects pause for 30 seconds. Practice is unranked and creates no match records.\n"
            +"League shows saved host records. History includes stats and replay access. Clear rounds, no daily obligations.";
    static Stage show(LanBall app,String title,Node node){
        app.release();Stage stage=new Stage();stage.initOwner(app.window);stage.setTitle(title);
        ScrollPane scroll=new ScrollPane(node);scroll.setFitToWidth(true);
        Scene scene=new Scene(scroll,820,620);scene.getRoot().setStyle(app.window.getScene().getRoot().getStyle());
        stage.setScene(scene);stage.show();return stage;
    }
    static TableView<String[]> table(String text){
        TableView<String[]> table=new TableView<>();String[] lines=text.strip().split("\n");String[] headings=lines[0].split("\t");
        if(headings.length<10)table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for(int i=0;i<headings.length;i++){final int column=i;TableColumn<String[],String> c=new TableColumn<>(headings[i]);
            c.setCellValueFactory(cell->new SimpleStringProperty(column<cell.getValue().length?cell.getValue()[column]:""));
            c.setPrefWidth(i==0?200:85);table.getColumns().add(c);}
        for(int i=1;i<lines.length;i++)table.getItems().add(lines[i].split("\t",-1));table.setPrefHeight(340);return table;
    }
    static void response(LanBall app,String data){
        String kind=data.substring(2,data.indexOf('\n')), body=data.substring(data.indexOf('\n')+1);
        if(kind.equals("replay")){replay(app,body);return;}
        TableView<String[]> table=table(body);TextField player=new TextField(),season=new TextField();
        if(kind.equals("league")){
            Wire.background(()->{try{League.atomic(app.settings.resolveSibling("league-cache.txt"),data);}catch(Exception ignored){}});
            show(app,"LEAGUE / host standings",new VBox(12,LanBall.flow(LanBall.field("Player",player),LanBall.field("Season",season),
                    LanBall.button("Filter",()->app.send("Q\tleague\t"+season.getText()+"\t"+player.getText())),
                    LanBall.button("Player history",()->app.send("Q\thistory\t"+player.getText())),
                    LanBall.button("League / season names",()->names(app))),table));
        }else{
            Button watch=LanBall.button("Watch selected replay",()->{String[] row=table.getSelectionModel().getSelectedItem();
                if(row!=null)app.send("Q\treplay\t"+row[0]);});
            TextField first=new TextField(),second=new TextField();Label comparison=new Label("Choose two players to compare.");
            Button compare=LanBall.button("Head to head",()->{
                int[] wins=new int[3];Map<String,List<String[]>> matches=new LinkedHashMap<>();
                for(String[] row:table.getItems())matches.computeIfAbsent(row[0],id->new ArrayList<>()).add(row);
                for(List<String[]> rows:matches.values()){
                    String[] a=rows.stream().filter(r->r[5].equalsIgnoreCase(first.getText())).findFirst().orElse(null);
                    String[] b=rows.stream().filter(r->r[5].equalsIgnoreCase(second.getText())).findFirst().orElse(null);
                    if(a==null||b==null||a[6].equals(b[6])||!a[2].equals("Completed"))continue;
                    int red=Integer.parseInt(a[3]),blue=Integer.parseInt(a[4]);
                    wins[red==blue?1:((red>blue)==a[6].equals("0")?0:2)]++;
                }comparison.setText(first.getText()+": "+wins[0]+" wins · "+wins[1]+" draws · "+wins[2]+" losses");
            });
            Button graph=LanBall.button("Selected player's Elo graph",()->{
                String[] selected=table.getSelectionModel().getSelectedItem();if(selected==null)return;
                LineChart<Number,Number> chart=new LineChart<>(new NumberAxis(),new NumberAxis());
                XYChart.Series<Number,Number> series=new XYChart.Series<>();series.setName(selected[5]);int match=0;
                series.getData().add(new XYChart.Data<>(0,1000));
                for(String[] row:table.getItems())if(row[5].equals(selected[5]))
                    series.getData().add(new XYChart.Data<>(++match,Integer.parseInt(row[14])));
                chart.getData().add(series);show(app,"Rating history",chart);
            });
            show(app,"MATCH HISTORY / reports",new VBox(12,table,LanBall.flow(watch,graph),
                    LanBall.flow(LanBall.field("First player",first),LanBall.field("Opponent",second),compare),comparison));
        }
    }
    static void names(LanBall app){
        if(app.server==null||!app.server.editable()){app.notice("The host changes league settings between matches.");return;}
        TextField league=new TextField(app.server.league.settings.getProperty("name","Campus League"));
        TextField season=new TextField(app.server.season);
        show(app,"League / season",new VBox(12,LanBall.field("League",league),LanBall.field("Season",season),
                LanBall.button("Save",()->{try{app.server.league.setting("name",Wire.clean(league.getText(),40));
                    app.server.league.setting("season",Wire.clean(season.getText(),40));app.server.season=Wire.clean(season.getText(),40);
                }catch(Exception error){app.notice(error.getMessage());}})));
    }
    static void configure(LanBall app){
        if(app.server==null){app.notice("Host a match first.");return;}
        Server s=app.server;TextField seconds=new TextField(""+s.duration),goals=new TextField(""+s.target);
        TextField size=new TextField(""+s.teamSize),width=new TextField(""+s.width),height=new TextField(""+s.height);
        TextField opening=new TextField(""+s.opening),name=new TextField("My pitch");CheckBox ranked=new CheckBox("Ranked / standard pitch");
        ranked.setSelected(s.ranked);ComboBox<String> library=new ComboBox<>();
        s.league.settings.stringPropertyNames().stream().filter(k->k.startsWith("pitch.")).forEach(library.getItems()::add);
        library.setOnAction(e->{String[] p=s.league.settings.getProperty(library.getValue()).split(",");
            width.setText(p[0]);height.setText(p[1]);opening.setText(p[2]);name.setText(library.getValue().substring(6));});
        Label result=new Label();
        Button apply=LanBall.button("Apply / preview pitch",()->{try{result.setText(s.configure(Integer.parseInt(seconds.getText()),
                Integer.parseInt(goals.getText()),Integer.parseInt(size.getText()),ranked.isSelected(),Double.parseDouble(width.getText()),
                Double.parseDouble(height.getText()),Double.parseDouble(opening.getText())));}catch(Exception error){result.setText("Use valid numbers.");}});
        Button save=LanBall.button("Save named pitch",()->{try{apply.fire();if(!s.message.startsWith("Settings applied"))return;
            String key="pitch."+Wire.clean(name.getText(),40);if(s.league.settings.containsKey(key)&&!app.confirm("Replace this saved pitch?"))return;
            s.league.setting(key,s.width+","+s.height+","+s.opening);if(!library.getItems().contains(key))library.getItems().add(key);
        }catch(Exception error){app.notice(error.getMessage());}});
        show(app,"MATCH LAB / pitch library",new VBox(12,LanBall.flow(LanBall.field("Seconds 30–600",seconds),
                LanBall.field("Goal limit",goals),LanBall.field("Players per side 1–3",size)),ranked,
                LanBall.flow(LanBall.field("Width 720–1280",width),LanBall.field("Height 420–760",height),LanBall.field("Goal opening",opening)),
                LanBall.field("Pitch name",name),library,LanBall.flow(apply,save),result));
    }
    static void settings(LanBall app){
        VBox box=new VBox(14);String[] labels={"Mute sounds","Reduce motion","Smooth network movement","High contrast"};
        String[] ids={"mute","motion","smooth","contrast"};
        for(int i=0;i<ids.length;i++){String id=ids[i];CheckBox check=new CheckBox(labels[i]);
            check.setSelected(Boolean.parseBoolean(app.preferences.getProperty(id,id.equals("smooth")?"true":"false")));
            check.setOnAction(e->{app.preferences.setProperty(id,""+check.isSelected());app.theme();app.saveSettings();});box.getChildren().add(check);}
        Slider volume=new Slider(0,1,app.arena.volume);volume.valueProperty().addListener((o,a,b)->{
            app.preferences.setProperty("volume",""+b.doubleValue());app.theme();app.saveSettings();});
        box.getChildren().addAll(new Label("Sound volume"),volume,new Label("Select a button, then press a key (arrows remain available)."));
        String[] actions={"Left","Right","Up","Down","Kick"};
        for(int i=0;i<5;i++){final int index=i;Button key=new Button(actions[i]);key.setOnKeyPressed(e->{
            app.preferences.setProperty("key"+index,e.getCode().name());key.setText(actions[index]+": "+e.getCode());app.saveSettings();});
            box.getChildren().add(key);}
        ComboBox<String> fonts=new ComboBox<>();fonts.getItems().addAll("14","16","18","20");fonts.setValue(app.preferences.getProperty("font","14"));
        fonts.setOnAction(e->{app.preferences.setProperty("font",fonts.getValue());app.theme();app.saveSettings();});
        box.getChildren().addAll(new Label("Text size"),fonts);show(app,"SETTINGS / make it yours",box);
    }
    static void replay(LanBall app,String text){
        String[] frames=text.split("\n~\n");Arena arena=new Arena();arena.muted=true;
        Slider seek=new Slider(0,frames.length-1,0);seek.valueProperty().addListener((o,a,b)->arena.accept(frames[b.intValue()]));
        Timeline play=new Timeline(new KeyFrame(Duration.millis(33),e->seek.setValue(Math.min(frames.length-1,seek.getValue()+1))));
        play.setCycleCount(Timeline.INDEFINITE);arena.accept(frames[0]);
        Stage stage=show(app,"REPLAY / "+frames.length+" frames",new VBox(10,arena,seek,LanBall.flow(
                LanBall.button("Play",play::play),LanBall.button("Pause",play::pause),LanBall.button("Next goal",()->{
                    for(int i=(int)seek.getValue()+1;i<frames.length;i++)if(frames[i].startsWith("H\tGoal")
                            &&!frames[i-1].startsWith("H\tGoal")){seek.setValue(i);break;}}))));
        arena.widthProperty().bind(stage.widthProperty().subtract(40));arena.heightProperty().bind(arena.widthProperty().multiply(.59));
        stage.setOnHidden(e->{play.stop();arena.animation.stop();});
    }
    static void tournament(LanBall app){
        if(app.server==null){app.notice("Tournament management is available on the host.");return;}
        Server s=app.server;TextField name=new TextField(s.league.settings.getProperty("cup.name","Campus Cup"));
        TextArea rosters=new TextArea(s.league.settings.getProperty("cup.rosters","Team A:player1\nTeam B:player2\nTeam C:player3\nTeam D:player4"));
        Label note=new Label("Four lines: Team name:player,player. Each player belongs to one team.");
        ComboBox<String> fixtures=new ComboBox<>();String[] teams=s.league.settings.getProperty("cup.rosters","").split("\n");
        for(int a=0;a<teams.length;a++)for(int b=a+1;b<teams.length;b++)fixtures.getItems().add(a+"-"+b+"  "+teams[a].split(":")[0]+" vs "+teams[b].split(":")[0]);
        Button create=LanBall.button("Create tournament",()->{try{
            String[] rows=rosters.getText().strip().split("\n");Set<String> used=new HashSet<>();if(rows.length!=4)throw new Exception("Exactly four teams required.");
            for(String row:rows){String[] pair=row.split(":");if(pair.length!=2||pair[0].isBlank())throw new Exception("Use Team:players.");
                for(String player:pair[1].split(","))if(s.league.profile(player.strip())==null||!used.add(player.strip().toLowerCase(Locale.ROOT)))
                    throw new Exception("Unknown or repeated player: "+player);}
            s.league.setting("cup.name",Wire.clean(name.getText(),40));s.league.setting("cup.rosters",rosters.getText().strip());
            s.league.setting("cup.id",UUID.randomUUID().toString());app.notice("Saved. Reopen Tournament to select a fixture.");
        }catch(Exception error){app.notice(error.getMessage());}});
        Button select=LanBall.button("Select fixture",()->{if(fixtures.getValue()!=null && s.editable()){
            s.fixture=s.league.settings.getProperty("cup.id","")+":"+fixtures.getValue().split(" ")[0];s.message="Choose the fixture rosters and mark ready.";}});
        int[][] points=new int[teams.length][5];StringBuilder results=new StringBuilder("Fixture\tRed\tBlue\tStatus\n");
        for(String record:s.league.matches()){String[] h=record.split("\n")[0].split("\t",-1);
            if(h[10].startsWith(s.league.settings.getProperty("cup.id","none")))results.append(h[10]).append('\t').append(h[4])
                    .append('\t').append(h[5]).append('\t').append(h[3]).append('\n');
            if(h[10].startsWith(s.league.settings.getProperty("cup.id","none"))&&h[3].equals("Completed")){
                String[] pair=h[10].split(":")[1].split("-");int r=Integer.parseInt(h[4]),b=Integer.parseInt(h[5]);
                for(int side=0;side<2;side++){int[] row=points[Integer.parseInt(pair[side])];int scored=side==0?r:b,against=side==0?b:r;
                    row[0]++;row[1]+=scored;row[2]+=against;row[3]=row[1]-row[2];row[4]+=scored>against?3:scored==against?1:0;}
            }}
        List<Integer> order=new ArrayList<>();for(int i=0;i<teams.length;i++)order.add(i);
        order.sort(Comparator.<Integer>comparingInt(i->points[i][4]).thenComparingInt(i->points[i][3])
                .thenComparingInt(i->points[i][1]).reversed());
        StringBuilder standing=new StringBuilder("Team\tPlayed\tFor\tAgainst\tDifference\tPoints\n");
        for(int i:order){standing.append(teams[i].split(":")[0]);for(int value:points[i])standing.append('\t').append(value);standing.append('\n');}
        Button clear=LanBall.button("Ordinary match",()->{if(s.editable())s.fixture="";});
        show(app,"TOURNAMENT / round robin",new VBox(12,LanBall.field("Name",name),note,rosters,create,fixtures,
                LanBall.flow(select,clear),table(standing.toString()),table(results.toString())));
    }
}
