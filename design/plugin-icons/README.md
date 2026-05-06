# Git Worktrees Icon Assets

These SVGs are based on the supplied branch-with-checkmark glyph.

- `src/main/resources/META-INF/pluginIcon.svg` is the 40x40 light-theme plugin logo.
- `src/main/resources/META-INF/pluginIcon_dark.svg` is the 40x40 dark-theme plugin logo.
- `worktrees-icon-template.svg` keeps colors adjustable through CSS variables.
- `worktrees-glyph.svg` is a transparent glyph that follows `currentColor` and `--check`.
- `worktrees-icon-*.svg` are ready-to-use promotional variants.
- `worktrees-promo-card.svg` is a larger square-corner promotional card.
- `preview.html` shows all variants and includes controls for size and colors.

For the template icon, override the variables on the root `<svg>` element:

```html
<svg style="--icon-bg: #147a70; --icon-fg: white; --icon-check: #8bf0c3">
  ...
</svg>
```

CSS variables and `currentColor` do not cross into an SVG loaded through
`<img src="...">`. To recolor the template without editing the file, inline the
SVG or copy the file and change the root `style` values.
