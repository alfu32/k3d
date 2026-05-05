<script lang="ts">
  import { base } from '$app/paths';

  export let src: string;
  export let alt: string;
  export let caption = '';

  $: resolvedSrc = src.startsWith('/') ? `${base}${src}` : src;
</script>

<figure>
  <img src={resolvedSrc} {alt} loading="lazy" />
  {#if caption}
    <figcaption>{caption}</figcaption>
  {/if}
</figure>

<style>
  figure {
    margin: 1.2rem 0;
  }

  img {
    display: block;
    width: 100%;
    height: auto;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel-soft);
  }

  figcaption {
    margin-top: 0.45rem;
    color: var(--muted);
    font-size: 0.92rem;
  }
</style>
