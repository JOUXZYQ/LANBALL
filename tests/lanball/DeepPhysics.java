package lanball;
import java.io.*;
import java.util.*;

public class DeepPhysics {
 static final long BASE=1000000000000L;
 static Match match(){Match m=new Match();m.phase="Playing";m.remaining=600;m.goalLimit=20;m.resumeAt=0;return m;}
 static Player add(Match m,String n,int team,double x,double y,int input){Player p=new Player(n);p.team=team;p.x=x;p.y=y;p.input=input;p.lastInput=BASE;m.players.add(p);return p;}
 static void step(Match m,int i){long now=BASE+Math.round((i+1)*1e9/120);for(Player p:m.players)p.lastInput=now;m.step(Match.STEP,now);}
 static double overlap(Match m){double worst=0;for(int i=0;i<m.players.size();i++){Player p=m.players.get(i);worst=Math.max(worst,24-p.distanceTo(m.ball));for(int j=i+1;j<m.players.size();j++)worst=Math.max(worst,32-p.distanceTo(m.players.get(j)));}return worst;}
 static String initial(Match m){StringBuilder b=new StringBuilder();for(Player p:m.players)b.append(String.format(Locale.ROOT,"%s:(%.8f,%.8f,input=%d) ",p.name,p.x,p.y,p.input));return b+String.format(Locale.ROOT,"ball:(%.8f,%.8f,%.8f,%.8f)",m.ball.x,m.ball.y,m.ball.vx,m.ball.vy);}
 static Match copy(Match m,boolean reverse){Match c=match();c.ball.x=m.ball.x;c.ball.y=m.ball.y;c.ball.vx=m.ball.vx;c.ball.vy=m.ball.vy;for(Player p:m.players)add(c,p.name,p.team,p.x,p.y,p.input);if(reverse)Collections.reverse(c.players);return c;}
 static void trace(Match m,String path,int ticks)throws Exception{try(PrintWriter out=new PrintWriter(path)){out.println("tick,ballx,bally,vx,vy,overlap,players");for(int i=0;i<ticks;i++){step(m,i);StringBuilder ps=new StringBuilder();for(Player p:m.players)ps.append(String.format(Locale.ROOT,"%s:%.9f:%.9f;",p.name,p.x,p.y));out.printf(Locale.ROOT,"%d,%.9f,%.9f,%.9f,%.9f,%.9f,%s%n",i,m.ball.x,m.ball.y,m.ball.vx,m.ball.vy,overlap(m),ps);}}}
 public static void main(String[] args)throws Exception{
  Locale.setDefault(Locale.ROOT);String dir=args[0];new File(dir).mkdirs();
  for(int hz:new int[]{30,60,65,66,80,100,119,120,121,144,240}){Match m=match();Player a=add(m,"Alice",0,100,100,2);for(int i=0;i<hz;i++){long now=BASE+Math.round((i+1)*1e9/hz);a.lastInput=now;m.update(1.0/hz,now);}System.out.printf("TIMESTEP hz=%d distance=%.9f expected=210 remaining=%.9f%n",hz,a.x-100,m.remaining);}
  // A near-expiry shot that cannot cross the line before time zero.
  for(double left:new double[]{.001,.010,.050}){Match m=match();m.remaining=left;m.ball.x=960;m.ball.y=270;m.ball.vx=500;boolean ended=m.update(.1,BASE);System.out.printf("AFTER_WHISTLE remainingAtStart=%.3f elapsed=.1 red=%d phase=%s ended=%s%n",left,m.red,m.phase,ended);}
  // Tick straddles the kickoff time: only 1ms was active but the entire 200ms is deducted and simulated.
  {Match m=match();Player a=add(m,"Alice",0,100,100,2);m.remaining=30;m.resumeAt=BASE-1000000L;m.update(.2,BASE);System.out.printf("COUNTDOWN_CROSS active=.001 elapsed=.2 remaining=%.6f movement=%.6f%n",m.remaining,a.x-100);}
  // Equal kick intent just inside contact, rather than the original test's non-contact distance of 30.
  for(double half:new double[]{16,20,23.5,24,24.1,25,30,39.9}){Match orig=match();add(orig,"Alice",0,480-half,270,16);add(orig,"Bobby",1,480+half,270,16);double[] v=new double[2];for(int rev=0;rev<2;rev++){Match m=copy(orig,rev==1);step(m,0);v[rev]=m.ball.vx;}System.out.printf("EQUAL_KICK half=%.1f vxForward=%.6f vxReverse=%.6f%n",half,v[0],v[1]);}
  // Three-player symmetric squeeze: all initial circles are tangent and therefore valid.
  for(int n:new int[]{3,4,5,6}){Match m=match();double radius=n<5?24:32;for(int i=0;i<n;i++){double theta=2*Math.PI*i/n;add(m,"P"+i,i%2,480+radius*Math.cos(theta),270+radius*Math.sin(theta),0);}double max=0;for(int i=0;i<600;i++){step(m,i);max=Math.max(max,overlap(m));}System.out.printf("STATIONARY_RING n=%d ball=(%.6f,%.6f) worstOverlap=%.6f%n",n,m.ball.x,m.ball.y,max);}
  // Search from valid, initially non-overlapping player and ball positions near the corner.
  Random rng=new Random(8251026);double bestOverlap=0,bestJump=0,bestOrder=0;Match overlapStart=null,jumpStart=null,orderStart=null;
  int accepted=0,attempted=0,invalidFinal=0,nonfinite=0,cycles=0;long steps=0;
  while(accepted<3000&&attempted++<60000){int n=2+rng.nextInt(5);Match initial=match();boolean valid=true;
   for(int i=0;i<n;i++){Player p=add(initial,"P"+i,i%2,18+rng.nextDouble()*170,18+rng.nextDouble()*170,5);for(int j=0;j<i;j++)if(p.distanceTo(initial.players.get(j))<32){valid=false;break;}if(!valid)break;}
   if(!valid)continue;initial.ball.x=8+rng.nextDouble()*140;initial.ball.y=8+rng.nextDouble()*140;for(Player p:initial.players)if(p.distanceTo(initial.ball)<24)valid=false;if(!valid)continue;
   accepted++;Match m=copy(initial,false);double jump=0,ov=0,lastDX=0,lastDY=0;int reversals=0;
   for(int i=0;i<480;i++){double x=m.ball.x,y=m.ball.y;step(m,i);steps++;if(i>=360){double dx=m.ball.x-x,dy=m.ball.y-y;jump=Math.max(jump,Math.hypot(dx,dy));ov=Math.max(ov,overlap(m));if(dx*lastDX+dy*lastDY<.0&&Math.hypot(dx,dy)>.25&&Math.hypot(lastDX,lastDY)>.25)reversals++;lastDX=dx;lastDY=dy;}for(Player p:m.players){if(p.x<18||p.x>942||p.y<18||p.y>522)invalidFinal++;if(!Double.isFinite(p.x)||!Double.isFinite(p.y))nonfinite++;}}
   if(reversals>20)cycles++;if(ov>bestOverlap){bestOverlap=ov;overlapStart=initial;}if(reversals>20&&jump>bestJump){bestJump=jump;jumpStart=initial;}
   if(accepted%100==0)System.out.printf("SEARCH_PROGRESS cases=%d steps=%d worstOverlap=%.6f cyclicJump=%.6f%n",accepted,steps,bestOverlap,bestJump);
  }
  System.out.printf("SEARCH accepted=%d attempts=%d steps=%d worstOverlap=%.9f cyclicJump=%.9f cycles=%d outside=%d nonfinite=%d%n",accepted,attempted,steps,bestOverlap,bestJump,cycles,invalidFinal,nonfinite);
  if(overlapStart!=null){System.out.println("WORST_OVERLAP "+initial(overlapStart));trace(copy(overlapStart,false),dir+"/crowd-overlap.csv",1200);}
  if(jumpStart!=null){System.out.println("WORST_CYCLE "+initial(jumpStart));trace(copy(jumpStart,false),dir+"/corner-cycle.csv",1200);}
  // Local three-player valid initial triangles converging around the ball; compare both iteration orders.
  Random orderRng=new Random(8808);int orderCases=0;
  for(int t=0;t<10000;t++){Match initial=match();int n=3;boolean valid=true;
   for(int i=0;i<n;i++){double angle=2*Math.PI*i/n+(orderRng.nextDouble()-.5)*.5,radius=24+orderRng.nextDouble()*20;Player p=add(initial,"P"+i,i%2,480+radius*Math.cos(angle),270+radius*Math.sin(angle),16);for(int j=0;j<i;j++)if(p.distanceTo(initial.players.get(j))<32)valid=false;}
   if(!valid)continue;orderCases++;Match a=copy(initial,false),b=copy(initial,true);step(a,0);step(b,0);double diff=Math.hypot(a.ball.vx-b.ball.vx,a.ball.vy-b.ball.vy);if(diff>bestOrder){bestOrder=diff;orderStart=initial;}
  }
  System.out.printf("KICK_ORDER_SEARCH cases=%d maxVelocityDifference=%.9f%n",orderCases,bestOrder);
  if(orderStart!=null&&bestOrder>.1){System.out.println("KICK_ORDER_SETUP "+initial(orderStart));}
  System.out.println("DONE");
 }
}
