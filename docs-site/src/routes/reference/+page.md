# Reference

This section documents technical surfaces that matter to users, plugin authors, tutorial authors, and local automation.

<div class="card-grid">
  <a class="doc-card" href="./commands/"><strong>Commands</strong>Palette and MCP command discovery strategy.</a>
  <a class="doc-card" href="./tools/"><strong>Tools</strong>Built-in tool families and generated command naming.</a>
  <a class="doc-card" href="./panels/"><strong>Panels</strong>Right-side panels and known view commands.</a>
  <a class="doc-card" href="./file-format/"><strong>File Format</strong>Saved model data and compatibility notes.</a>
  <a class="doc-card" href="./mcp/"><strong>MCP</strong>Local automation HTTP and MCP JSON-RPC surface.</a>
  <a class="doc-card" href="./console/"><strong>Console</strong>Groovy runtime, bindings, and safety boundary.</a>
  <a class="doc-card" href="./plugin-api/"><strong>Plugin API</strong>Groovy plugin interface and capability model.</a>
  <a class="doc-card" href="./webcomponent/"><strong>Webcomponent</strong>Browser tutorial-facing contract.</a>
</div>

## Generated Reference Policy

Command and tool references should be regenerated from the current runtime when exact command IDs matter. The repository contains command IDs in source and historical docs, but `/scene/listCommands` is the runtime source of truth for automation.
