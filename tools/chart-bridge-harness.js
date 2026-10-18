#!/usr/bin/env node
/**
 * Headless verification of the Kotlin <-> Lightweight Charts bridge.
 *
 * Replays the exact JavaScript statements produced by `ChartCommands`
 * (exported by `ChartPayloadFixtureTest`) against the REAL
 * `lightweight-charts@5.2.1` bundle running inside jsdom + node-canvas.
 *
 * Prerequisites:
 *   ./gradlew testDebugUnitTest            (produces the payload fixture)
 *   npm install jsdom canvas               (in any directory)
 *
 * Run:
 *   JSDOM_PATH=<dir>/jsdom CANVAS_PATH=<dir>/canvas node tools/chart-bridge-harness.js
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { JSDOM } = require(process.env.JSDOM_PATH || 'jsdom');
const { createCanvas } = require(process.env.CANVAS_PATH || 'canvas');

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'app/src/main/assets');
const FIXTURE = path.join(ROOT, 'app/build/chart-bridge-fixture/payload.json');

const results = [];
let failures = 0;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function check(name, fn) {
  try {
    const value = await fn();
    results.push({ name, ok: true });
    console.log(`  \u2713 ${name}${value === undefined ? '' : '  -> ' + JSON.stringify(value)}`);
  } catch (error) {
    failures += 1;
    results.push({ name, ok: false, error: error.message });
    console.log(`  \u2717 ${name}  -> ${error.message}`);
  }
}

const tick = (ms) => new Promise((resolve) => setTimeout(resolve, ms || 40));

async function main() {
  // -------------------------------------------------------------- jsdom --
  const rawHtml = fs.readFileSync(path.join(ASSETS, 'chart.html'), 'utf8');
  const inlineMatch = rawHtml.match(/<script>([\s\S]*?)<\/script>/);
  assert(inlineMatch, 'chart.html must contain exactly one inline module script');
  const inlineScript = inlineMatch[1];

  const hostHtml = rawHtml
    .replace(/<script src="[^"]*"><\/script>/, '')
    .replace(/<script>[\s\S]*?<\/script>/, '');

  const dom = new JSDOM(hostHtml, {
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    url: 'http://localhost/',
  });
  const { window } = dom;

  // node-canvas backing for every 2D context the chart asks for.
  window.HTMLCanvasElement.prototype.getContext = function (type) {
    if (type !== '2d') return null;
    const w = Math.max(1, this.width || 300);
    const h = Math.max(1, this.height || 150);
    if (!this.__nodeCanvas) this.__nodeCanvas = createCanvas(w, h);
    if (this.__nodeCanvas.width !== w) this.__nodeCanvas.width = w;
    if (this.__nodeCanvas.height !== h) this.__nodeCanvas.height = h;
    return this.__nodeCanvas.getContext('2d');
  };
  Object.defineProperty(window, 'devicePixelRatio', { value: 2, configurable: true });

  // jsdom ships neither of the browser observers the chart engine probes for.
  window.matchMedia = (query) => ({
    media: query,
    matches: false,
    onchange: null,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent: () => false,
  });

  window.ResizeObserver = class ResizeObserver {
    constructor(callback) { this.callback = callback; }
    observe() { this.callback([], this); }
    unobserve() {}
    disconnect() {}
  };

  const container = window.document.getElementById('container');
  const overlay = window.document.getElementById('overlay');
  [container, overlay].forEach((el) => {
    Object.defineProperty(el, 'clientWidth', { value: 1080, configurable: true });
    Object.defineProperty(el, 'clientHeight', { value: 1400, configurable: true });
  });

  // ------------------------------------------------------------- bridge --
  const bridge = { ready: 0, crosshair: [], reset: 0, drawings: [], logs: [] };
  window.AndroidBridge = {
    onChartReady: () => { bridge.ready += 1; },
    onCrosshairUpdate: (o, h, l, c, t) => { bridge.crosshair.push({ o, h, l, c, t }); },
    onCrosshairReset: () => { bridge.reset += 1; },
    onDrawingAdded: (type, count) => { bridge.drawings.push({ type, count }); },
    onLog: (message) => { bridge.logs.push(message); },
  };

  console.log('ApexTerminal chart bridge harness');
  console.log('---------------------------------');

  // ------------------------------------------------------- boot engine --
  await check('lightweight-charts bundle loads', () => {
    window.eval(fs.readFileSync(path.join(ASSETS, 'lightweight-charts.standalone.production.js'), 'utf8'));
    assert(window.LightweightCharts, 'window.LightweightCharts missing');
    return window.LightweightCharts.version();
  });

  await check('bundle exposes the v5 series definitions chart.html uses', () => {
    const api = window.LightweightCharts;
    ['createChart', 'CandlestickSeries', 'HistogramSeries', 'LineSeries', 'CrosshairMode']
      .forEach((key) => assert(api[key], `LightweightCharts.${key} is undefined`));
    return 'ok';
  });

  window.eval(inlineScript);
  await tick(60); // let DOMContentLoaded / load hooks fire, exactly like a real page

  await check('chart.html boots against the real engine and signals ready', () => {
    assert(bridge.logs.length === 0, 'chart reported a boot error: ' + bridge.logs.join(' | '));
    assert(bridge.ready === 1, `onChartReady fired ${bridge.ready} times`);
    return 'ready';
  });

  await check('chart created real canvases inside the container', () => {
    const canvases = container.querySelectorAll('canvas');
    assert(canvases.length > 0, 'no canvas element was created');
    return canvases.length + ' canvases';
  });

  // ---------------------------------------------------- replay commands --
  const fixture = JSON.parse(fs.readFileSync(FIXTURE, 'utf8'));
  const commands = fixture.commands;
  const drainLogs = () => {
    const captured = bridge.logs.slice();
    bridge.logs.length = 0;
    return captured;
  };

  await check('kotlin payload contains every command the viewport emits', () => {
    ['setInitialData', 'updateLiveTick', 'setEmaData', 'setRsiData', 'setStochData',
      'setDrawMode', 'fitContent', 'scrollToRealtime', 'clearDrawings'].forEach((key) => {
      assert(commands[key], `missing command ${key}`);
      assert(commands[key].startsWith('window.'), `${key} is not a window call`);
    });
    return Object.keys(commands).length + ' commands';
  });

  await check('setInitialData feeds 1000 candles into the candlestick series', () => {
    window.eval(commands.setInitialData);
    const logs = drainLogs();
    assert(logs.length === 0, 'setInitialData logged: ' + logs.join(' | '));
    return fixture.candleCount + ' candles';
  });

  await check('updateLiveTick applies a forming bar without throwing', () => {
    window.eval(commands.updateLiveTick);
    const logs = drainLogs();
    assert(logs.length === 0, 'updateLiveTick logged: ' + logs.join(' | '));
    return 'ok';
  });

  await check('ema overlays accept the three Kotlin series', () => {
    window.eval(commands.setEmaData);
    const logs = drainLogs();
    assert(logs.length === 0, 'setEmaData logged: ' + logs.join(' | '));
    return 'ok';
  });

  await check('rsi pane is created from Kotlin data', () => {
    window.eval(commands.setRsiData);
    const logs = drainLogs();
    assert(logs.length === 0, 'setRsiData logged: ' + logs.join(' | '));
    return 'ok';
  });

  await check('stochastic pane is created from Kotlin data', () => {
    window.eval(commands.setStochData);
    const logs = drainLogs();
    assert(logs.length === 0, 'setStochData logged: ' + logs.join(' | '));
    return 'ok';
  });

  await check('empty indicator payloads tear the panes back down', () => {
    window.eval('window.setRsiData(null);');
    window.eval('window.setStochData(null,null);');
    const logs = drainLogs();
    assert(logs.length === 0, 'teardown logged: ' + logs.join(' | '));
    window.eval(commands.setRsiData);
    window.eval(commands.setStochData);
    drainLogs();
    return 'ok';
  });

  await check('viewport commands are harmless', () => {
    window.eval(commands.fitContent);
    window.eval(commands.scrollToRealtime);
    drainLogs();
    return 'ok';
  });

  // ------------------------------------------------------------ drawing --
  await check('drawing mode switches the container into capture mode', () => {
    window.eval(commands.setDrawMode);
    assert(container.classList.contains('draw-mode'), 'draw-mode class was not applied');
    return 'trendline';
  });

  await check('a touch drag creates a drawing and notifies kotlin', () => {
    const dispatch = (type, x, y) => {
      const event = new window.Event(type, { bubbles: true, cancelable: true });
      const point = { clientX: x, clientY: y };
      event.touches = [point];
      event.changedTouches = [point];
      event.preventDefault = () => {};
      event.stopPropagation = () => {};
      container.dispatchEvent(event);
    };

    dispatch('touchstart', 200, 900);
    dispatch('touchmove', 700, 400);
    dispatch('touchend', 700, 400);

    assert(bridge.drawings.length === 1,
      `expected 1 onDrawingAdded callback, got ${bridge.drawings.length}`);
    assert(bridge.drawings[0].type === 'trendline',
      `unexpected drawing type ${bridge.drawings[0].type}`);
    assert(window.drawingCount() === 1, 'drawing was not retained');
    return JSON.stringify(bridge.drawings[0]);
  });

  await check('clearDrawings wipes the overlay', () => {
    window.eval(commands.clearDrawings);
    assert(window.drawingCount() === 0, 'drawings survived clearDrawings');
    return 'ok';
  });

  await check('crosshair mode returns the container to pan/zoom', () => {
    window.eval('window.setDrawMode("crosshair");');
    assert(!container.classList.contains('draw-mode'), 'draw-mode class was not removed');
    return 'ok';
  });

  await check('the clear pseudo-tool resets drawings and the mode', () => {
    window.eval('window.setDrawMode("trendline");');
    window.eval('window.setDrawMode("clear");');
    assert(window.drawingCount() === 0, 'clear tool left drawings behind');
    assert(!container.classList.contains('draw-mode'), 'clear tool left draw-mode on');
    return 'ok';
  });

  // ------------------------------------------------------- crosshair io --
  await check('crosshair notifications flow back to the Android bridge', () => {
    const rect = { left: 0, top: 0, width: 1080, height: 1400, right: 1080, bottom: 1400 };
    const canvases = Array.from(container.querySelectorAll('canvas'));
    assert(canvases.length > 0, 'no canvases to point at');

    window.eval('window.setDrawMode("crosshair");');
    const before = bridge.crosshair.length;

    canvases.forEach((canvas) => {
      Object.defineProperty(canvas, 'getBoundingClientRect', { value: () => rect, configurable: true });
      // The engine only starts its hover tracking from a mouseenter.
      canvas.dispatchEvent(new window.MouseEvent('mouseenter', { bubbles: false, cancelable: true }));
      [400, 620, 840].forEach((x) => {
        [300, 640, 980].forEach((y) => {
          canvas.dispatchEvent(new window.MouseEvent('mousemove', {
            bubbles: true, cancelable: true, clientX: x, clientY: y,
          }));
        });
      });
    });

    assert(bridge.crosshair.length > before,
      'no onCrosshairUpdate callback reached the bridge');
    const sample = bridge.crosshair[bridge.crosshair.length - 1];
    ['o', 'h', 'l', 'c', 't'].forEach((key) => {
      assert(typeof sample[key] === 'number', `crosshair payload missing numeric ${key}`);
    });
    assert(sample.h >= sample.l, 'crosshair high must be >= low');
    assert(sample.t > 1_600_000_000 && sample.t < 2_000_000_000,
      `crosshair time must be epoch seconds, got ${sample.t}`);
    return `${bridge.crosshair.length - before} callbacks, last t=${sample.t}`;
  });

  // --------------------------------------------------------- pixel proof --
  await tick(250); // allow the render loop to flush several frames

  await check('candles were actually rasterised (bullish blue present)', () => {
    const canvases = Array.from(container.querySelectorAll('canvas'));
    let blue = 0;
    let dark = 0;
    canvases.forEach((canvas) => {
      const nodeCanvas = canvas.__nodeCanvas;
      if (!nodeCanvas) return;
      const ctx = nodeCanvas.getContext('2d');
      const { width, height } = nodeCanvas;
      if (width < 4 || height < 4) return;
      const data = ctx.getImageData(0, 0, width, height).data;
      for (let i = 0; i < data.length; i += 4) {
        const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
        if (a < 8) continue;
        if (Math.abs(r - 37) < 26 && Math.abs(g - 99) < 26 && Math.abs(b - 235) < 26) blue += 1;
        if (Math.abs(r - 30) < 16 && Math.abs(g - 34) < 16 && Math.abs(b - 45) < 16) dark += 1;
      }
    });
    assert(blue > 200, `expected bullish #2563EB pixels on screen, found ${blue}`);
    return { bluePixels: blue, bearishPixels: dark };
  });

  await check('no runtime errors were reported from javascript', () => {
    assert(bridge.logs.length === 0, bridge.logs.join(' | '));
    return 'clean';
  });

  // ---------------------------------------------------------------- exit --
  console.log('---------------------------------');
  console.log(`${results.length - failures}/${results.length} checks passed`);
  if (failures) {
    results.filter((r) => !r.ok).forEach((f) => console.log(`FAILED: ${f.name} -> ${f.error}`));
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
