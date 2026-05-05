<script lang="ts">
  import commandCatalog from '$lib/data/generated/command_catalog.json';

  export let category = '';
  export let limit = 0;

  $: commands = commandCatalog.commands
    .filter((command) => !category || command.category === category)
    .slice(0, limit > 0 ? limit : undefined);
</script>

<div class="summary">
  <span>{commands.length} commands</span>
  <span>Generated {new Date(commandCatalog.generatedAt).toLocaleString()}</span>
  <span>Source {commandCatalog.source.endpoint}</span>
</div>

<div class="table-wrap">
  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Name</th>
        <th>Category</th>
        <th>Description</th>
      </tr>
    </thead>
    <tbody>
      {#each commands as command}
        <tr>
          <td><code>{command.id}</code></td>
          <td>{command.name}</td>
          <td>{command.category}</td>
          <td>{command.description}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

<style>
  .summary {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin: 1rem 0;
    color: var(--muted);
    font-size: 0.92rem;
  }

  .summary span {
    padding: 0.25rem 0.45rem;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: var(--panel);
  }

  .table-wrap {
    overflow-x: auto;
    margin: 1rem 0;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel);
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.92rem;
  }

  th,
  td {
    padding: 0.55rem 0.7rem;
    border-bottom: 1px solid var(--border);
    text-align: left;
    vertical-align: top;
  }

  th {
    background: var(--panel-soft);
    color: var(--text);
    font-weight: 700;
  }

  tr:last-child td {
    border-bottom: 0;
  }
</style>
