'use strict';
// The browser sends controls only. Scores, positions and collision rules come from Java.
const $ = id => document.getElementById(id);
const canvas = $('pitch'), ctx = canvas.getContext('2d');
const colors = ['#ff6678', '#63b5ff', '#8d9bb0'];
let token = '', myName = '', move = 0, kicking = false, keyboard = new Set();
let wantedTeam = -1, chat = '', timer = 0, inFlight = false, generation = 0;
let state = null, dots = new Map(), lastGoal = null, partyUntil = 0, particles = [];
let lastFrame = performance.now(), lastGood = 0, noticeUntil = 0;
let ballX = 480, ballY = 270;
let kickQueued = false;

function sendSoon() {
  if (token && !inFlight) { clearTimeout(timer); timer = setTimeout(poll, 0); }
}

async function api(path, body = '', session = token) {
  const controller = new AbortController(), timeout = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetch(path, {method:'POST',headers:{'Content-Type':'text/plain','X-LanBall':'1','X-Session':session},body,signal:controller.signal,cache:'no-store'});
    const text = await response.text();
    if (!response.ok) { const error = new Error(text); error.status = response.status; throw error; }
    return text;
  } finally { clearTimeout(timeout); }
}

$('joinForm').onsubmit = async event => {
  event.preventDefault(); $('joinButton').disabled = true; $('loginError').textContent = '';
  try {
    const text = await api('/join', [$('name').value.trim(),$('password').value,$('club').value.trim(),$('shirt').value,$('create').checked?'create':'login'].join('\n'));
    [token,myName] = text.split('\n'); generation++; state = null; dots.clear(); lastGoal = null; partyUntil = 0; particles = [];
    $('password').value = ''; $('create').checked = false; $('login').hidden = true; $('menu').hidden = false;
    $('identity').textContent = myName + ' · choose a team'; $('ready').checked = false;
    wantedTeam = -1; lastGood = performance.now(); clearControls(); poll();
  } catch(error) { $('loginError').textContent = error.name==='AbortError'?'Host is not responding. Check Wi-Fi.':error.message; }
  finally { $('joinButton').disabled = false; }
};

function controlBits() {
  let keys = move | (kicking || kickQueued?16:0) | ($('ready').checked?32:0);
  for (const key of keyboard) keys |= {a:1,ArrowLeft:1,d:2,ArrowRight:2,w:4,ArrowUp:4,s:8,ArrowDown:8,' ':16}[key] || 0;
  return keys;
}
async function poll() {
  if (!token || inFlight) return;
  const run = generation, began = performance.now(), team = wantedTeam, message = chat, keys = controlBits();
  wantedTeam = -1; chat = ''; kickQueued = false; inFlight = true;
  try {
    const frame = await api('/input', [keys,team,message].join('\n'));
    if (run !== generation) return;
    readFrame(frame); lastGood = performance.now();
  } catch(error) {
    if (run !== generation) return;
    clearControls();
    if (error.status===401 || performance.now()-lastGood>8000) { disconnected(error.message); return; }
    $('status').textContent = 'Connection interrupted · controls released';
  } finally {
    inFlight = false;
    if (token) timer = setTimeout(poll, keys!==controlBits() || wantedTeam>=0 ? 0 : Math.max(0, 25-(performance.now()-began)));
  }
}
function disconnected(reason) {
  token=''; generation++; clearTimeout(timer); clearControls(); state=null; dots.clear(); partyUntil=0;
  $('login').hidden=false; $('menu').hidden=true; $('loginError').textContent=reason;
}
$('leave').onclick=async()=>{const old=token;disconnected('You left the match.');try{await api('/leave','',old);}catch(error){/* Host expires an unreachable session. */}};
$('menuButton').onclick=()=>{clearControls();$('menu').hidden=false;};
$('closeMenu').onclick=()=>{$('menu').hidden=true;};
$('team').onchange=()=>{wantedTeam=Number($('team').value);$('ready').checked=false;sendSoon();};
$('ready').onchange=sendSoon;
$('chatForm').onsubmit=event=>{event.preventDefault();chat=$('chat').value.replace(/[\r\n\t]/g,' ').slice(0,80);$('chat').value='';};
$('profileDetails').ontoggle=async()=>{if($('profileDetails').open&&token)try{$('profile').textContent=await api('/profile');}catch(error){$('profile').textContent=error.message;}};

function readFrame(frame) {
  const next={players:[],bumpers:[],chat:[],league:[],goalHalf:72}, rows=frame.trim().split('\n');
  for (const row of rows) {
    const p=row.split('\t');
    if(p[0]==='H') Object.assign(next,{phase:p[1],seconds:+p[2],red:+p[3],blue:+p[4],bx:+p[5],by:+p[6],count:+p[7],limit:+p[8]});
    if(p[0]==='A'){next.pitch=p[1];next.goalHalf=+p[2];}
    if(p[0]==='O')next.bumpers.push({x:+p[1],y:+p[2],radius:+p[3]});
    if(p[0]==='P')next.players.push({name:p[1],team:+p[2],x:+p[3],y:+p[4],ready:p[5]==='true',connected:p[6]==='true',shirt:p[7]});
    if(p[0]==='G'){next.goal=+p[1];next.goalTeam=+p[2];next.goalText=p[3];}
    if(p[0]==='M')next.message=p[1];
    if(p[0]==='N'){next.notice=p[1];noticeUntil=performance.now()+3000;}
    if(p[0]==='C')next.chat.push(p[1]);
    if(p[0]==='S')next.season=p[1];
    if(p[0]==='L')next.league.push(p[1].padEnd(16)+' '+p[9]+' Elo · '+p[3]+'W '+p[4]+'D '+p[5]+'L');
  }
  if(!next.phase)throw new Error('Invalid host update.');
  if(!state || Math.hypot(ballX-next.bx,ballY-next.by)>100 || next.count>0){ballX=next.bx;ballY=next.by;}
  if(state && state.phase==='Playing' && next.phase!=='Playing')$('ready').checked=false;
  if(lastGoal!==null && next.goal>lastGoal)celebrate(next.goalTeam);
  lastGoal=next.goal; state=next;
  const present=new Set();
  for(const p of next.players){present.add(p.name);const old=dots.get(p.name);if(old){old.targetX=p.x;old.targetY=p.y;Object.assign(old,{team:p.team,shirt:p.shirt,connected:p.connected});if(Math.hypot(old.x-p.x,old.y-p.y)>100){old.x=p.x;old.y=p.y;}}else dots.set(p.name,{...p,targetX:p.x,targetY:p.y});}
  for(const name of dots.keys())if(!present.has(name))dots.delete(name);
  $('score').textContent=next.red+' : '+next.blue;
  $('clock').textContent=next.phase==='Playing'?Math.floor(next.seconds/60)+':'+String(next.seconds%60).padStart(2,'0')+' · first to '+next.limit:next.phase;
  if(next.notice)$('status').textContent=next.notice;else if(performance.now()>noticeUntil)$('status').textContent=next.message;
  const mine=next.players.find(p=>p.name===myName);
  if(mine){if(wantedTeam<0)$('team').value=String(mine.team);$('identity').textContent=myName+' · '+['Red →','← Blue','Watching'][mine.team];}
  $('team').disabled=next.phase==='Playing'||next.phase==='Save failed';
  $('roster').textContent=next.players.filter(p=>p.team<2).map(p=>p.name+' · '+['Red','Blue'][p.team]+(p.ready?' ✓':'')).join(' / ')+' — Host starts on the laptop.';
  $('messages').textContent=next.chat.join('\n');
  $('league').textContent=next.season+'\n\n'+(next.league.join('\n')||'Play a match to enter the standings.');
}

// Separate pointer IDs allow a left thumb to steer while the right thumb holds Kick.
let stickPointer=null, kickPointers=new Set();
function steer(event) {
  const previous=move;
  const r=$('stick').getBoundingClientRect(), dx=event.clientX-r.left-r.width/2, dy=event.clientY-r.top-r.height/2;
  const length=Math.hypot(dx,dy), max=r.width*.3, scale=length>max?max/length:1;
  $('thumb').style.transform=`translate(${dx*scale}px,${dy*scale}px)`;
  move=0;
  if(length>r.width*.12){const nx=dx/length,ny=dy/length;if(nx<-.38)move|=1;if(nx>.38)move|=2;if(ny<-.38)move|=4;if(ny>.38)move|=8;}
  if(previous!==move)sendSoon();
}
$('stick').onpointerdown=e=>{if(stickPointer!==null)return;e.preventDefault();stickPointer=e.pointerId;$('stick').setPointerCapture(e.pointerId);steer(e);};
$('stick').onpointermove=e=>{if(e.pointerId===stickPointer)steer(e);};
function releaseStick(e){if(e.pointerId===stickPointer){stickPointer=null;move=0;$('thumb').style.transform='';sendSoon();}}
for(const type of ['pointerup','pointercancel','lostpointercapture'])$('stick').addEventListener(type,releaseStick);
$('kick').onpointerdown=e=>{e.preventDefault();$('kick').setPointerCapture(e.pointerId);kickPointers.add(e.pointerId);kicking=true;kickQueued=true;$('kick').classList.add('pressed');sendSoon();};
function releaseKick(e){const held=kickPointers.delete(e.pointerId);kicking=kickPointers.size>0;if(e.type==='pointercancel'||(e.type==='lostpointercapture'&&held))kickQueued=false;$('kick').classList.toggle('pressed',kicking);sendSoon();}
for(const type of ['pointerup','pointercancel','lostpointercapture'])$('kick').addEventListener(type,releaseKick);
function clearControls(){move=0;kicking=false;kickQueued=false;stickPointer=null;kickPointers.clear();keyboard.clear();$('thumb').style.transform='';$('kick').classList.remove('pressed');sendSoon();}
window.addEventListener('keydown',e=>{if(!token||!$('menu').hidden||e.target.matches('input,select'))return;const key=e.key.length===1?e.key.toLowerCase():e.key;if(['a','d','w','s','ArrowLeft','ArrowRight','ArrowUp','ArrowDown',' '].includes(key)){e.preventDefault();keyboard.add(key);}});
window.addEventListener('keyup',e=>keyboard.delete(e.key.length===1?e.key.toLowerCase():e.key));
window.addEventListener('blur',clearControls);
document.addEventListener('visibilitychange',clearControls);
window.addEventListener('resize',clearControls);

function circle(x,y,r,fill,stroke){ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.stroke();}}
function label(text,x,y,size,color='#e9eff8'){ctx.fillStyle=color;ctx.font=`600 ${size}px system-ui`;ctx.textAlign='center';ctx.fillText(text,x,y);}
function celebrate(team){partyUntil=performance.now()+2100;particles=Array.from({length:80},()=>({x:Math.random()*960,y:-Math.random()*200,vx:(Math.random()-.5)*80,vy:80+Math.random()*130,color:Math.random()>.35?colors[team]:'#ffd27b'}));}
function draw(now) {
  const dt=Math.min(.05,(now-lastFrame)/1000);lastFrame=now;
  ctx.clearRect(0,0,1000,580);ctx.save();ctx.translate(20,20);
  const turf=ctx.createLinearGradient(0,0,960,540);turf.addColorStop(0,'#20483f');turf.addColorStop(1,'#142f2c');
  ctx.fillStyle=turf;ctx.fillRect(0,0,960,540);
  for(let i=0;i<12;i+=2){ctx.fillStyle='#ffffff05';ctx.fillRect(i*80,0,80,540);}
  ctx.strokeStyle='#658c80';ctx.lineWidth=2;ctx.strokeRect(3,3,954,534);ctx.beginPath();ctx.moveTo(480,3);ctx.lineTo(480,537);ctx.stroke();circle(480,270,70,null,'#658c80');circle(480,270,3,'#91b6a8');
  ctx.strokeRect(3,155,105,230);ctx.strokeRect(852,155,105,230);
  const goal=state?state.goalHalf:72;
  ctx.fillStyle=colors[0];ctx.fillRect(-10,270-goal,9,goal*2);ctx.fillStyle=colors[1];ctx.fillRect(961,270-goal,9,goal*2);
  if(state){
    for(const b of state.bumpers)circle(b.x,b.y,b.radius,'#435267','#a0b0c4');
    if(state.phase!=='Lobby'){
      for(const p of dots.values())if(p.team<2){const blend=1-Math.exp(-40*dt);p.x+=(p.targetX-p.x)*blend;p.y+=(p.targetY-p.y)*blend;ctx.globalAlpha=p.connected?1:.4;if(p.name===myName){ctx.lineWidth=3;circle(p.x,p.y,21,null,'#ffd27b');}circle(p.x,p.y,16,colors[p.team]);label(p.shirt,p.x,p.y+5,13,'#10151f');label(p.name,p.x,p.y-29,12);ctx.globalAlpha=1;}
      const blend=1-Math.exp(-40*dt);ballX+=(state.bx-ballX)*blend;ballY+=(state.by-ballY)*blend;
      circle(ballX+2,ballY+4,9,'#00000040');circle(ballX,ballY,8,'#f4f6fc','#10151f');
    }
    if(now<partyUntil){for(const p of particles){p.x+=p.vx*dt;p.y+=p.vy*dt;ctx.fillStyle=p.color;ctx.fillRect(p.x,p.y,6,10);}banner('GOAL!',state.goalText,colors[state.goalTeam]);}
    else if(state.phase==='Result')banner(state.red===state.blue?'FULL TIME':state.red>state.blue?'RED WINS':'BLUE WINS','Open Menu for standings and your profile.','#ffd27b');
    else if(state.phase==='Save failed')banner('MATCH FINISHED','Host is retrying the save.','#ffd27b');
    else if(state.phase==='Lobby')banner('GET READY','Menu → choose a team → Ready. Host starts the match.','#ffd27b');
    else if(state.count>0)label(String(state.count),480,300,100,'#ffd27b');
  }else banner('LANBALL','One pitch. Phone and desktop players.','#ffd27b');
  ctx.restore();requestAnimationFrame(draw);
}
function banner(title,subtitle,color){ctx.fillStyle='#0b141ee8';ctx.fillRect(100,191,760,158);label(title,480,260,48,color);label(subtitle,480,307,17);}
requestAnimationFrame(draw);
