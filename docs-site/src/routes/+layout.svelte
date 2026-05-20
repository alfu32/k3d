<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/stores';
  import '../app.css';
  import { navGroups, topNavigation } from '$lib/data/navigation';

  $: currentPath = base && $page.url.pathname.startsWith(base)
    ? $page.url.pathname.slice(base.length) || '/'
    : $page.url.pathname;

  $: activeGroup = navGroups.find((group) =>
    currentPath === group.href || currentPath.startsWith(group.href)
  );
  $: isPlayground = currentPath === '/playground/' || currentPath.startsWith('/playground/');

  function hrefFor(href: string): string {
    return href === '/' ? `${base}/` : `${base}${href}`;
  }

  function isActive(href: string): boolean {
    if (href === '/') {
      return currentPath === '/';
    }
    return currentPath === href || currentPath.startsWith(href);
  }
</script>

<svelte:head>
  <title>Octodraw Documentation</title>
  <meta
    name="description"
    content="Documentation, tutorials, reference, and local automation workflow for Octodraw."
  />
</svelte:head>

<header class="site-header">
  <a class="brand" href={hrefFor('/')}>
    <span class="brand-mark">O</span>
    <span>Octodraw Docs</span>
  </a>
  <nav class="top-nav" aria-label="Primary">
    {#each topNavigation as item}
      {#if item.external}
        <a href={item.href} rel="noreferrer" target="_blank">{item.label}</a>
      {:else}
        <a
          href={hrefFor(item.href)}
          class:active={isActive(item.href)}
          aria-current={isActive(item.href) ? 'page' : undefined}
        >
          {item.label}
        </a>
      {/if}
    {/each}
  </nav>
</header>

<div class="site-shell" class:with-sidebar={activeGroup} class:playground-shell={isPlayground}>
  {#if activeGroup}
    <aside class="sidebar" aria-label={`${activeGroup.title} navigation`}>
      <a class="sidebar-title" href={hrefFor(activeGroup.href)}>{activeGroup.title}</a>
      <nav>
        {#each activeGroup.items as item}
          <a
            href={hrefFor(item.href)}
            class:active={isActive(item.href)}
            aria-current={isActive(item.href) ? 'page' : undefined}
          >
            {item.label}
          </a>
        {/each}
      </nav>
    </aside>
  {/if}

  <main id="content" class="content" tabindex="-1">
    <slot />
  </main>
</div>

{#if !isPlayground}
  <footer class="site-footer">
    <p>
      Octodraw is a Kotlin/libGDX direct modeling project. Documentation source is in
      <a href="https://github.com/alfu32/k3d">alfu32/k3d</a>.
    </p>
  </footer>
{/if}
