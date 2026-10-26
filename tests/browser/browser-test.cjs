// Optional: needs Node + Playwright and BrowserHost running (see README). PLAYWRIGHT_MODULE may point at a Playwright install.
const {chromium, webkit, devices}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('fs');
const assert=require('assert');
// LB_ENGINE=webkit runs the same flow on Playwright WebKit (Safari engine). LB_SHOTS picks the screenshot folder.
const ENGINE=process.env.LB_ENGINE||'chrome',SHOTS=process.env.LB_SHOTS||'screenshots';
// WebKit has no CDP touch input, so dispatch real PointerEvents with two pointer IDs.
// Synthetic pointers cannot be captured, so capture is stubbed in this mode only.
async function webkitTouch(page){
 await page.evaluate(()=>{Element.prototype.setPointerCapture=function(){};});
 let down=new Map();
 return {send:async(_,{type,touchPoints})=>page.evaluate(([type,pts,prev])=>{
   const fire=(kind,p)=>{const el=document.elementFromPoint(p.x,p.y)||document.body;const target=p.target?document.getElementById(p.target):el;
     target.dispatchEvent(new PointerEvent(kind,{pointerId:p.id,pointerType:'touch',clientX:p.x,clientY:p.y,bubbles:true,cancelable:true,isPrimary:p.id===1}));return target.id;};
   const ids=new Set(pts.map(p=>p.id));const out={};
   if(type==='touchStart'){for(const p of pts)if(!prev[p.id])out[p.id]=fire('pointerdown',p);else out[p.id]=prev[p.id];}
   else{for(const id in prev)if(!ids.has(+id))fire(type==='touchCancel'?'pointercancel':'pointerup',{id:+id,x:0,y:0,target:prev[id]});}
   return out;},[type,touchPoints,Object.fromEntries(down)]).then(r=>{down=new Map(Object.entries(r||{}));})};
}
(async()=>{
 const browser=ENGINE==='webkit'?await webkit.launch({headless:true}):await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 const errors=[];
 const redContext=await browser.newContext({...devices['iPhone 13'],...(ENGINE==='webkit'?{}:{browserName:undefined})});
 const blueContext=await browser.newContext({...devices['Pixel 7'],...(ENGINE==='webkit'?{}:{browserName:undefined})});
 const red=await redContext.newPage(),blue=await blueContext.newPage();
 for(const page of [red,blue])page.on('pageerror',e=>errors.push(e.message));
 const stamp=String(Date.now()).slice(-6);
 async function join(page,name,team){
   await page.goto('http://127.0.0.1:45930');
   await page.locator('#name').fill(name);await page.locator('#password').fill('demo123');
   await page.locator('#joinButton').click();await page.locator('#menu').waitFor({state:'visible'});
   await page.locator('#team').selectOption(String(team));
   await page.waitForTimeout(160);await page.locator('#ready').check();
   await page.locator('#closeMenu').click();
 }
 await red.goto('http://127.0.0.1:45930');await red.screenshot({path:SHOTS+'/mobile-login.png'});
 await join(red,'Red'+stamp,0);await join(blue,'Blue'+stamp,1);
 await red.waitForFunction(()=>state&&state.phase==='Playing'&&state.count===0);
 console.log('PASS two browser players join, ready and start');
 for(const page of [red,blue])assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'mobile horizontal overflow');
 await red.screenshot({path:SHOTS+'/mobile-portrait.png'});
 const before=await red.evaluate(()=>state.players.find(p=>p.name===myName).x);
 // Chromium's touch input dispatch creates two independent simultaneous physical pointer IDs.
 const touch=ENGINE==='webkit'?await webkitTouch(red):await redContext.newCDPSession(red);
 const cdp=touch;
 const stick=await red.locator('#stick').boundingBox(),kick=await red.locator('#kick').boundingBox();
 const left={x:Math.round(stick.x+stick.width*.83),y:Math.round(stick.y+stick.height*.5),id:1};
 const right={x:Math.round(kick.x+kick.width*.5),y:Math.round(kick.y+kick.height*.5),id:2};
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[left]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[left,right]});
 await red.waitForTimeout(300);
 assert(await red.evaluate(()=>(controlBits()&18)===18),'simultaneous move/kick');
 const after=await red.evaluate(()=>state.players.find(p=>p.name===myName).x);assert(after>before+15,'touch movement updates game');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 await red.waitForTimeout(80);assert(await red.evaluate(()=>(controlBits()&31)===0),'touch end releases controls');
 console.log('PASS real two-touch movement plus kick, then release');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[left]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});
 assert(await red.evaluate(()=>move===0),'pointer cancellation');
 await red.setViewportSize({width:844,height:390});await red.waitForTimeout(100);
 await red.screenshot({path:SHOTS+'/mobile-landscape.png'});
 assert(await red.evaluate(()=>document.documentElement.scrollWidth<=innerWidth && document.documentElement.scrollHeight<=innerHeight),'landscape overflow');
 console.log('PASS touch cancellation, portrait and landscape layouts');
 await red.locator('#menuButton').click();await red.locator('#chat').fill('Hello Android');await red.locator('#chatForm button').click();
 await blue.locator('#menuButton').click();await blue.waitForFunction(()=>document.getElementById('messages').textContent.includes('Hello Android'));
 await red.locator('#leave').click();await red.locator('#login').waitFor({state:'visible'});
 await red.locator('#password').fill('demo123');await red.locator('#joinButton').click();await red.locator('#menu').waitFor({state:'visible'});
 assert(await red.locator('#team').inputValue()==='0','reconnect red team');
 console.log('PASS chat delivery and sign-in reconnect');
 await red.locator('#leave').click();await blue.locator('#leave').click();
 assert.deepEqual(errors,[]);console.log('PASS no browser JavaScript errors');
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
