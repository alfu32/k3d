<script lang="ts">
  import { base } from '$app/paths';
  import { onMount } from 'svelte';

  type ToolbarIconGuideItem = {
    name: string;
    label?: string;
    hint?: string;
  };

  export let title = 'Toolbar buttons';
  export let items: ToolbarIconGuideItem[] = [];
  export let iconSize: 32 | 48 | 64 = 32;
  export let columns = 4;
  export let sheetSrc = `${base}/icons.svg`;
  export let mappingSrc = `${base}/icons.mapping.csv`;

  type IconEntry = {
    name: string;
    order: number;
    line: number;
    col: number;
    startX: number;
    endX: number;
    startY: number;
    endY: number;
  };

  let iconMap: Record<string, IconEntry> = {};
  let loading = true;
  let error = '';

  function parseCsv(text: string): Record<string, IconEntry> {
    const lines = text.split(/\r?\n/).filter(Boolean);
    if (lines.length <= 1) {
      return {};
    }
    const headers = lines[0].split('|').map((value) => value.trim());
    const indexOf = (key: string) => headers.indexOf(key);
    const startXKey = iconSize === 64 ? 'start_x_64' : iconSize === 48 ? 'start_x_48' : 'start_x';
    const endXKey = iconSize === 64 ? 'end_x_64' : iconSize === 48 ? 'end_x_48' : 'end_x';
    const startYKey = iconSize === 64 ? 'start_y_64' : iconSize === 48 ? 'start_y_48' : 'start_y';
    const endYKey = iconSize === 64 ? 'end_y_64' : iconSize === 48 ? 'end_y_48' : 'end_y';

    return Object.fromEntries(
      lines.slice(1).map((line) => {
        const parts = line.split('|').map((value) => value.trim());
        const entry: IconEntry = {
          name: parts[indexOf('name')] ?? '',
          order: Number(parts[indexOf('order')] ?? 0),
          line: Number(parts[indexOf('line')] ?? 0),
          col: Number(parts[indexOf('col')] ?? 0),
          startX: Number(parts[indexOf(startXKey)] ?? 0),
          endX: Number(parts[indexOf(endXKey)] ?? 0),
          startY: Number(parts[indexOf(startYKey)] ?? 0),
          endY: Number(parts[indexOf(endYKey)] ?? 0)
        };
        return [entry.name, entry];
      })
    );
  }

  onMount(async () => {
    try {
      const response = await fetch(mappingSrc);
      if (!response.ok) {
        throw new Error(`Failed to load ${mappingSrc} (${response.status})`);
      }
      iconMap = parseCsv(await response.text());
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
    } finally {
      loading = false;
    }
  });

  function entryFor(item: ToolbarIconGuideItem): IconEntry | undefined {
    return iconMap[item.name];
  }

  function iconCropStyle(entry: IconEntry): string {
    const width = entry.endX - entry.startX + 1;
    const height = entry.endY - entry.startY + 1;
    return `--icon-x: ${entry.startX}px; --icon-y: ${entry.startY}px; --icon-w: ${width}px; --icon-h: ${height}px;`;
  }

  function iconLabel(item: ToolbarIconGuideItem): string {
    return item.label ?? item.name;
  }
</script>

<section class="guide">
  {#if title}
    <div class="heading">
      <h3>{title}</h3>
      <p>Use the exact button icon and label shown here when following the tutorial steps.</p>
    </div>
  {/if}

  {#if error}
    <p class="status" role="alert">{error}</p>
  {:else if loading}
    <p class="status" role="status">Loading toolbar icons...</p>
  {:else if items.length === 0}
    <p class="status">No toolbar buttons were requested.</p>
  {:else}
    <div class="grid" style={`--columns: ${columns};`}>
      {#each items as item}
        {@const entry = entryFor(item)}
        <article class="tile">
          <div class="icon" aria-hidden="true">
            {#if entry}
              <div class="sprite" style={`${iconCropStyle(entry)} background-image: url(${sheetSrc});`} />
            {:else}
              <div class="missing">{item.name}</div>
            {/if}
          </div>

          <div class="copy">
            <strong>{iconLabel(item)}</strong>
            <code>{item.name}</code>
            {#if item.hint}
              <p>{item.hint}</p>
            {/if}
          </div>
        </article>
      {/each}
    </div>
  {/if}
</section>

<style>
  .guide {
    margin: 1.2rem 0;
    padding: 1rem;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel);
  }

  .heading h3 {
    margin: 0;
    font-size: 1.05rem;
  }

  .heading p,
  .status,
  .copy p {
    margin: 0.35rem 0 0;
    color: var(--muted);
    font-size: 0.92rem;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 0.75rem;
  }

  .tile {
    display: grid;
    gap: 0.7rem;
    padding: 0.75rem;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel-soft);
  }

  .icon {
    display: grid;
    place-items: center;
    min-height: 84px;
    border: 1px solid var(--border);
    border-radius: 6px;
    background: white;
  }

  .sprite {
    width: var(--icon-w);
    height: var(--icon-h);
    background-repeat: no-repeat;
    background-position: calc(var(--icon-x) * -1) calc(var(--icon-y) * -1);
    image-rendering: pixelated;
  }

  .missing {
    padding: 0.5rem 0.75rem;
    color: var(--muted);
    font-size: 0.9rem;
  }

  .copy strong,
  .copy code {
    display: block;
  }

  .copy code {
    width: fit-content;
    margin-top: 0.2rem;
  }
</style>
