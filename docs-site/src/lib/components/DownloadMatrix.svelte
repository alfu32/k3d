<script lang="ts">
  const downloads = [
    {
      title: 'Desktop builds',
      body: 'Use GitHub Releases for packaged desktop artifacts. Prefer the latest release page instead of version-pinned URLs.',
      href: 'https://github.com/alfu32/k3d/releases/latest',
      action: 'Latest release'
    },
    {
      title: 'Linux Snap',
      body: 'The project is published as Octodraw on Snapcraft when release packaging is available.',
      href: 'https://snapcraft.io/octodraw',
      action: 'Snapcraft'
    },
    {
      title: 'Android',
      body: 'Android builds exist, with current interaction best suited to mouse and keyboard input.',
      href: 'https://play.google.com/store/apps/details?id=com.github.alfu32.sketch',
      action: 'Google Play'
    },
    {
      title: 'Source and web',
      body: 'Build desktop, web runtime, and webcomponent artifacts from the repository with Gradle.',
      href: 'https://github.com/alfu32/k3d',
      action: 'Repository'
    }
  ];
</script>

<section class="download-matrix" aria-label="Download and build options">
  {#each downloads as item}
    <article>
      <h3>{item.title}</h3>
      <p>{item.body}</p>
      <a href={item.href} rel="noreferrer" target="_blank">{item.action}</a>
    </article>
  {/each}
</section>

<style>
  .download-matrix {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 0.9rem;
    margin: 1rem 0 1.5rem;
  }

  article {
    padding: 1rem;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--panel);
  }

  h3 {
    margin: 0 0 0.45rem;
    font-size: 1.05rem;
  }

  p {
    margin: 0 0 0.85rem;
    color: var(--muted);
  }
</style>
