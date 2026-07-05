export type NavItem = {
  label: string;
  href: string;
  external?: boolean;
};

export type NavGroup = {
  title: string;
  href: string;
  items: NavItem[];
};

export const topNavigation: NavItem[] = [
  { label: 'Home', href: '/' },
  { label: 'Guide', href: '/guide/' },
  { label: 'Tutorials', href: '/tutorials/' },
  { label: 'Playground', href: '/playground/' },
  { label: 'Reference', href: '/reference/' },
  { label: 'Automation', href: '/automation/' },
  { label: 'Specification', href: '/specification/' },
  { label: 'GitHub', href: 'https://github.com/alfu32/k3d', external: true }
];

export const navGroups: NavGroup[] = [
  {
    title: 'Guide',
    href: '/guide/',
    items: [
      { label: 'Install', href: '/guide/install/' },
      { label: 'Interface', href: '/guide/interface/' },
      { label: 'Navigation', href: '/guide/navigation/' },
      { label: 'Selection', href: '/guide/selection/' },
      { label: 'Snapping', href: '/guide/snapping/' },
      { label: 'Drawing Tools', href: '/guide/drawing-tools/' },
      { label: 'Modify Tools', href: '/guide/modify-tools/' },
      { label: 'Objects', href: '/guide/objects/' },
      { label: 'Dimensions', href: '/guide/dimensions/' },
      { label: 'Lighting', href: '/guide/lighting/' },
      { label: 'Files', href: '/guide/files/' },
      { label: 'Shortcuts', href: '/guide/shortcuts/' }
    ]
  },
  {
    title: 'Tutorials',
    href: '/tutorials/',
    items: [
      { label: 'Getting Started', href: '/tutorials/getting-started/' },
      { label: 'Draw a Box', href: '/tutorials/draw-a-box/' },
      { label: 'Modify a Box', href: '/tutorials/modify-a-box/' },
      { label: 'Translate Arrays', href: '/tutorials/translate-arrays/' },
      { label: 'Rotate Arrays', href: '/tutorials/rotate-arrays/' },
      { label: 'Volume and Fuzzy Tools', href: '/tutorials/volume-and-fuzzy-tools/' },
      { label: 'Push/Pull House', href: '/tutorials/push-pull-house/' },
      { label: 'Snapping Basics', href: '/tutorials/snapping-basics/' },
      { label: 'Objects and Instances', href: '/tutorials/objects-and-instances/' },
      { label: 'Lighting and Export', href: '/tutorials/lighting-and-export/' }
    ]
  },
  {
    title: 'Reference',
    href: '/reference/',
    items: [
      { label: 'Commands', href: '/reference/commands/' },
      { label: 'Tools', href: '/reference/tools/' },
      { label: 'Panels', href: '/reference/panels/' },
      { label: 'File Format', href: '/reference/file-format/' },
      { label: 'MCP', href: '/reference/mcp/' },
      { label: 'Console', href: '/reference/console/' },
      { label: 'Plugin API', href: '/reference/plugin-api/' },
      { label: 'Webcomponent', href: '/reference/webcomponent/' }
    ]
  },
  {
    title: 'Automation',
    href: '/automation/',
    items: [
      { label: 'Codex Workflow', href: '/automation/codex-workflow/' },
      { label: 'Codex Interoperability', href: '/automation/codex-interoperability/' },
      { label: 'MCP Contract', href: '/automation/mcp-contract/' },
      { label: 'Screenshot Pipeline', href: '/automation/screenshot-pipeline/' },
      { label: 'Doc Generation', href: '/automation/doc-generation/' },
      { label: 'QA Checklist', href: '/automation/qa-checklist/' }
    ]
  },
  {
    title: 'Specification',
    href: '/specification/',
    items: [
      { label: 'Site Spec', href: '/specification/site-spec/' },
      { label: 'Tutorial Spec', href: '/specification/tutorial-spec/' },
      { label: 'Webcomponent Contract', href: '/specification/webcomponent-contract/' },
      { label: 'Content Style Guide', href: '/specification/content-style-guide/' }
    ]
  }
];
