(function () {
  const currentScript = document.currentScript;
  const baseUrl = currentScript && currentScript.src
    ? new URL('.', currentScript.src).href
    : new URL('.', window.location.href).href;

  const runtimeState = window.__octodrawComponentRuntime || (window.__octodrawComponentRuntime = {
    baseUrl,
    loadPromise: null,
    ready: false,
    started: false,
    host: null,
    hostId: null,
    canvasId: null,
    pendingResolvers: new Map(),
    sequence: 0,
    readyResolvers: []
  });

  const replayableEventTypes = new Set(['ready', 'change', 'selectionchange', 'toolchange', 'error']);
  const propertyBackedEventTypes = new Set(['ready', 'change', 'selectionchange', 'toolchange', 'error']);

  function nextId(prefix) {
    runtimeState.sequence += 1;
    return `${prefix}-${runtimeState.sequence}`;
  }

  function normalizeModel(value) {
    if (value == null) {
      return null;
    }
    return typeof value === 'string' ? value : JSON.stringify(value);
  }

  function parseMaybeJson(value) {
    if (value == null || value === '') {
      return null;
    }
    if (typeof value !== 'string') {
      return value;
    }
    try {
      return JSON.parse(value);
    } catch (_) {
      return value;
    }
  }

  function ensureEmbedState(host) {
    if (runtimeState.host && runtimeState.host !== host && !runtimeState.host.isConnected) {
      runtimeState.host = null;
      runtimeState.hostId = null;
      runtimeState.canvasId = null;
    }
    if (runtimeState.host && runtimeState.host !== host) {
      throw new Error('Only one <octodraw-editor> instance is supported per page in the current runtime.');
    }
    runtimeState.host = host;
    runtimeState.hostId = host.id || nextId('octodraw-editor');
    host.id = runtimeState.hostId;
    runtimeState.canvasId = host._canvas ? host._canvas.id : runtimeState.canvasId;
    const nextState = window.__octodrawEmbedState || {};
    nextState.hostId = runtimeState.hostId;
    nextState.canvasId = runtimeState.canvasId;
    nextState.baseUrl = runtimeState.baseUrl;
    nextState.padHorizontal = 0;
    nextState.padVertical = 0;
    nextState.initialModel = normalizeModel(host._initialValue);
    nextState.initialFileName = host._initialFileName || null;
    nextState.toolbarsVisible = host._toolbarsVisible !== false;
    nextState.panelsVisible = host._panelsVisible !== false;
    nextState.commandQueue = nextState.commandQueue || [];
    window.__octodrawEmbedState = nextState;
  }

  function syncEmbedLayout(host) {
    ensureEmbedState(host);
  }

  function dispatchComponentEvent(target, type, detail) {
    const event = new CustomEvent(type, {
      detail,
      bubbles: true,
      composed: true
    });
    target.dispatchEvent(event);
    return event;
  }

  function applyRuntimeState(host) {
    window.__octodrawEmbedState = {
      hostId: runtimeState.hostId,
      canvasId: runtimeState.canvasId,
      baseUrl: runtimeState.baseUrl,
      padHorizontal: 0,
      padVertical: 0,
      initialModel: normalizeModel(host._initialValue),
      initialFileName: host._initialFileName || null,
      toolbarsVisible: host._toolbarsVisible !== false,
      panelsVisible: host._panelsVisible !== false,
      commandQueue: []
    };
  }

  function waitForReady() {
    if (runtimeState.ready) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      runtimeState.readyResolvers.push(resolve);
    });
  }

  function ensureRuntimeLoaded(host) {
    ensureEmbedState(host);
    if (runtimeState.loadPromise) {
      return runtimeState.loadPromise.then(() => waitForReady());
    }
    runtimeState.loadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = new URL('octodraw-runtime.js', runtimeState.baseUrl).href;
      script.async = true;
      script.onload = () => {
        try {
          if (!runtimeState.started) {
            if (typeof window.main !== 'function') {
              reject(new Error('Octodraw runtime loaded but main() is not available.'));
              return;
            }
            runtimeState.started = true;
            window.main();
          }
          resolve();
        } catch (error) {
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      };
      script.onerror = () => reject(new Error('Failed to load octodraw-runtime.js'));
      document.head.appendChild(script);
    });
    return runtimeState.loadPromise.then(() => waitForReady());
  }

  function queueCommand(host, command, payload) {
    return ensureRuntimeLoaded(host).then(() => {
      const requestId = nextId('octodraw-cmd');
      const commandQueue = window.__octodrawEmbedState && window.__octodrawEmbedState.commandQueue;
      if (!commandQueue) {
        throw new Error('Octodraw runtime command queue is unavailable.');
      }
      return new Promise((resolve, reject) => {
        runtimeState.pendingResolvers.set(requestId, { resolve, reject });
        commandQueue.push({
          requestId,
          command,
          payload: payload || null
        });
      });
    });
  }

  class OctodrawEditorElement extends HTMLElement {
    constructor() {
      super();
      this._initialValue = null;
      this._initialFileName = null;
      this._toolbarsVisible = true;
      this._panelsVisible = true;
      this._lastModel = null;
      this._lastRuntimeEvents = new Map();
      this._runtimeReady = false;
      this._connected = false;
      this._eventPropertyHandlers = Object.create(null);
      this._onReady = (event) => {
        runtimeState.ready = true;
        this._runtimeReady = true;
        if (event && event.detail && Object.prototype.hasOwnProperty.call(event.detail, 'model')) {
          this._lastModel = event.detail.model;
        }
        const resolvers = runtimeState.readyResolvers.splice(0, runtimeState.readyResolvers.length);
        resolvers.forEach((resolve) => resolve());
      };
      this._onChange = (event) => {
        if (event && event.detail && Object.prototype.hasOwnProperty.call(event.detail, 'model')) {
          this._lastModel = event.detail.model;
        }
      };
      this._onResponse = (event) => {
        const detail = event.detail || {};
        const requestId = detail.requestId;
        if (!requestId) {
          return;
        }
        const pending = runtimeState.pendingResolvers.get(requestId);
        if (!pending) {
          return;
        }
        runtimeState.pendingResolvers.delete(requestId);
        if (detail.success) {
          pending.resolve(detail.payload);
        } else {
          pending.reject(new Error(detail.message || 'Octodraw command failed.'));
        }
      };
    }

    __octodrawHandleRuntimeEvent(type, detail) {
      const event = dispatchComponentEvent(this, type, detail);
      if (replayableEventTypes.has(type)) {
        this._lastRuntimeEvents.set(type, event);
      }
      if (propertyBackedEventTypes.has(type)) {
        const handler = this._eventPropertyHandlers[type];
        if (typeof handler === 'function') {
          handler.call(this, event);
        }
      }
    }

    __octodrawHandleCommandResponse(detail) {
      const event = dispatchComponentEvent(this, '__octodraw-command-response', detail);
      this._onResponse(event);
    }

    addEventListener(type, listener, options) {
      super.addEventListener(type, listener, options);
      if (!replayableEventTypes.has(type)) {
        return;
      }
      const lastEvent = this._lastRuntimeEvents.get(type);
      if (!lastEvent) {
        return;
      }
      queueMicrotask(() => {
        if (typeof listener === 'function') {
          listener.call(this, lastEvent);
        } else if (listener && typeof listener.handleEvent === 'function') {
          listener.handleEvent(lastEvent);
        }
      });
    }

    whenReady() {
      return ensureRuntimeLoaded(this);
    }

    connectedCallback() {
      if (this._connected) {
        return;
      }
      this._connected = true;
      this.style.display = this.style.display || 'block';
      if (!this.style.minHeight) {
        this.style.minHeight = '480px';
      }
      this._toolbarsVisible = this.getAttribute('toolbars-visible') !== 'false';
      this._panelsVisible = this.getAttribute('panels-visible') !== 'false';
      this._initialFileName = this.getAttribute('file-name') || this._initialFileName;
      this.textContent = '';
      const canvas = document.createElement('canvas');
      canvas.id = nextId('octodraw-canvas');
      canvas.style.display = 'block';
      const suppressContextMenu = (event) => {
        event.preventDefault();
        event.stopPropagation();
        return false;
      };
      this.addEventListener('contextmenu', suppressContextMenu);
      canvas.addEventListener('contextmenu', suppressContextMenu);
      this.appendChild(canvas);
      this._canvas = canvas;
      applyRuntimeState(this);
      this._syncEmbedLayout = () => syncEmbedLayout(this);
      this._resizeObserver = typeof window.ResizeObserver === 'function'
        ? new ResizeObserver(() => this._syncEmbedLayout())
        : null;
      if (this._resizeObserver) {
        this._resizeObserver.observe(this);
      }
      window.addEventListener('resize', this._syncEmbedLayout);
      this.addEventListener('ready', this._onReady);
      this.addEventListener('change', this._onChange);
      this._syncEmbedLayout();
      ensureRuntimeLoaded(this).catch((error) => {
        this.__octodrawHandleRuntimeEvent('error', {
          message: error && error.message ? error.message : String(error)
        });
      });
    }

    disconnectedCallback() {
      this.removeEventListener('ready', this._onReady);
      this.removeEventListener('change', this._onChange);
      if (this._resizeObserver) {
        this._resizeObserver.disconnect();
        this._resizeObserver = null;
      }
      if (this._syncEmbedLayout) {
        window.removeEventListener('resize', this._syncEmbedLayout);
      }
      if (runtimeState.host === this) {
        runtimeState.host = null;
        runtimeState.hostId = null;
        runtimeState.canvasId = null;
        runtimeState.pendingResolvers.forEach((pending) => {
          pending.reject(new Error('<octodraw-editor> was disconnected before the command completed.'));
        });
        runtimeState.pendingResolvers.clear();
        const nextState = window.__octodrawEmbedState || {};
        nextState.hostId = null;
        nextState.canvasId = null;
        nextState.commandQueue = [];
        window.__octodrawEmbedState = nextState;
      }
      this._connected = false;
    }

    get value() {
      return this._lastModel != null ? this._lastModel : this._initialValue;
    }

    set value(nextValue) {
      this._initialValue = nextValue;
      if (window.__octodrawEmbedState && runtimeState.host === this && !this._runtimeReady) {
        window.__octodrawEmbedState.initialModel = normalizeModel(nextValue);
      }
      if (this._runtimeReady) {
        this.setModel(nextValue).catch((error) => {
          this.__octodrawHandleRuntimeEvent('error', {
            message: error && error.message ? error.message : String(error)
          });
        });
      }
    }

    get controller() {
      return this;
    }

    getModel() {
      return queueCommand(this, 'model.get').then((payload) => {
        if (payload && Object.prototype.hasOwnProperty.call(payload, 'model')) {
          this._lastModel = payload.model;
          return payload.model;
        }
        return this._lastModel;
      });
    }

    setModel(model, fileName) {
      const payload = { model: normalizeModel(model) };
      if (fileName) {
        payload.fileName = fileName;
      }
      return queueCommand(this, 'model.set', payload).then((result) => {
        if (result && Object.prototype.hasOwnProperty.call(result, 'model')) {
          this._lastModel = result.model;
        }
        return result;
      });
    }

    exec(command, payload) {
      return queueCommand(this, command, payload);
    }

    play(steps) {
      const list = Array.isArray(steps) ? steps : [];
      let chain = Promise.resolve();
      list.forEach((step) => {
        chain = chain.then(() => {
          if (!step || !step.cmd) {
            throw new Error('Each play() step requires a cmd field.');
          }
          const payload = Object.assign({}, step);
          delete payload.cmd;
          return this.exec(step.cmd, payload);
        });
      });
      return chain;
    }

    setUi(options) {
      return this.exec('ui.setVisibility', {
        toolbarsVisible: options && Object.prototype.hasOwnProperty.call(options, 'toolbarsVisible') ? !!options.toolbarsVisible : this._toolbarsVisible,
        panelsVisible: options && Object.prototype.hasOwnProperty.call(options, 'panelsVisible') ? !!options.panelsVisible : this._panelsVisible
      }).then((result) => {
        if (options && Object.prototype.hasOwnProperty.call(options, 'toolbarsVisible')) {
          this._toolbarsVisible = !!options.toolbarsVisible;
        }
        if (options && Object.prototype.hasOwnProperty.call(options, 'panelsVisible')) {
          this._panelsVisible = !!options.panelsVisible;
        }
        return result;
      });
    }

    getSelection() {
      return this.exec('selection.get');
    }

    clearSelection() {
      return this.exec('selection.clear');
    }

    getCamera() {
      return this.exec('camera.get');
    }

    setCamera(options) {
      return this.exec('camera.set', options || {});
    }

    selectTool(tool) {
      return this.exec('tool.select', { tool });
    }

    cancelTool() {
      return this.exec('tool.cancel');
    }

    pointer(event) {
      return this.exec('tool.pointer', event || {});
    }

    resize() {
      syncEmbedLayout(this);
    }
  }

  Object.defineProperties(OctodrawEditorElement.prototype, {
    onready: {
      get() { return this._eventPropertyHandlers.ready || null; },
      set(handler) {
        this._eventPropertyHandlers.ready = typeof handler === 'function' ? handler : null;
        const lastEvent = this._lastRuntimeEvents.get('ready');
        if (lastEvent && this._eventPropertyHandlers.ready) {
          queueMicrotask(() => this._eventPropertyHandlers.ready.call(this, lastEvent));
        }
      }
    },
    onchange: {
      get() { return this._eventPropertyHandlers.change || null; },
      set(handler) {
        this._eventPropertyHandlers.change = typeof handler === 'function' ? handler : null;
      }
    },
    onselectionchange: {
      get() { return this._eventPropertyHandlers.selectionchange || null; },
      set(handler) {
        this._eventPropertyHandlers.selectionchange = typeof handler === 'function' ? handler : null;
      }
    },
    ontoolchange: {
      get() { return this._eventPropertyHandlers.toolchange || null; },
      set(handler) {
        this._eventPropertyHandlers.toolchange = typeof handler === 'function' ? handler : null;
      }
    },
    onerror: {
      get() { return this._eventPropertyHandlers.error || null; },
      set(handler) {
        this._eventPropertyHandlers.error = typeof handler === 'function' ? handler : null;
      }
    }
  });

  if (!window.customElements.get('octodraw-editor')) {
    window.customElements.define('octodraw-editor', OctodrawEditorElement);
  }

  window.OctodrawEditorElement = OctodrawEditorElement;
  window.OctodrawEditorRuntime = {
    baseUrl,
    parseMaybeJson
  };
})();
