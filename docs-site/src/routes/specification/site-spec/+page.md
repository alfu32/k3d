# Site Specification

The docs site is a SvelteKit static application under `docs-site/`.

## Required Stack

- SvelteKit
- Svelte 4
- TypeScript
- `@sveltejs/adapter-static`
- mdsvex
- GitHub Actions
- GitHub Pages

Do not replace it with Vue, VitePress, Nuxt, Docusaurus, or React.

## Static Output

The production build writes to `docs-site/build` and uses base path `/k3d`. Development uses an empty base path. All routes are prerendered and must avoid server-only dependencies.

## Deployment

The Pages workflow builds the Gradle webcomponent, copies `web/build/dist/webcomponent` into `docs-site/static/webcomponent`, validates tutorial JSON, checks Svelte, builds the static site, uploads `docs-site/build`, and deploys to GitHub Pages.
