# VPN Monitor Design System

## 0. Research Log

- Embedded refs: shortlisted [linear.app, vercel, stripe] → picked redesign-skill.md + taste-skill.md because this is an existing UI redesign, not greenfield
- Lazyweb: skipped — existing UI audit, not brand research
- Imagen drafts: skipped — redesign preserves current dark glass aesthetic
- Skipped lanes: none

## 1. Atmosphere & Identity

A dark command center for network monitoring. Glass surfaces float over an animated mesh gradient wallpaper, creating depth through layered transparency. The signature is **muted glassmorphism** — surfaces separated by blur + subtle borders rather than shadows, with a single electric-blue accent that carries through every interactive element. Dense data display when needed (server cards, stats), spacious when not (settings forms). The UI should feel like a premium network operations dashboard, not a generic admin panel.

## 2. Color

### Palette (Dark Mode Only — no light mode)

| Role | Token | Value | Usage |
|------|-------|-------|-------|
| Background/base | --bg-base | #05070D | Page background, deepest layer |
| Wallpaper/overlay | --bg-overlay | rgba(5,7,13,0.6) | Overlay on mesh wallpaper |
| Glass/fill | --glass-fill | linear-gradient(155deg, rgba(255,255,255,0.14), rgba(255,255,255,0.03)) | Card surfaces |
| Glass/fill-flat | --glass-fill-flat | rgba(255,255,255,0.07) | Flat glass surfaces |
| Glass/border | --glass-border | rgba(255,255,255,0.16) | Card borders, dividers |
| Glass/border-strong | --glass-border-strong | rgba(255,255,255,0.28) | Emphasized borders |
| Text/primary | --text-primary | rgba(255,255,255,0.94) | Headlines, body |
| Text/secondary | --text-secondary | rgba(255,255,255,0.62) | Captions, hints |
| Text/tertiary | --text-tertiary | rgba(255,255,255,0.38) | Disabled, metadata |
| Accent/blue | --blue | #0A84FF | Primary accent, CTAs, links, focus |
| Accent/blue-soft | --blue-soft | rgba(10,132,255,0.16) | Accent backgrounds |
| Status/up | --green | #30D158 | Success, VLESS OK |
| Status/up-soft | --green-soft | rgba(48,209,88,0.16) | Success backgrounds |
| Status/warning | --orange | #FF9F0A | Degraded, caution |
| Status/warning-soft | --orange-soft | rgba(255,159,10,0.16) | Warning backgrounds |
| Status/down | --red | #FF453A | Error, VLESS FAIL |
| Status/down-soft | --red-soft | rgba(255,69,58,0.16) | Error backgrounds |
| Mesh/primary | --mesh-1 | #3B82F6 | Animated blob 1 |
| Mesh/secondary | --mesh-2 | #7C6FC9 | Animated blob 2 |
| Mesh/accent | --mesh-3 | #D1548A | Animated blob 3 |
| Mesh/cyan | --mesh-4 | #22D3EE | Animated blob 4 |

### Rules
- Dark mode only — no light mode toggle
- Single accent color (blue #0A84FF) for all interactive elements
- Status colors (green/orange/red) used only for semantic indicators
- Glass surfaces use gradient fill, not solid colors
- All surfaces have backdrop-filter: blur(30px) saturate(180%)

## 3. Typography

### Scale

| Level | Size | Weight | Line Height | Tracking | Usage |
|-------|------|--------|-------------|----------|-------|
| Header/title | 26px | 700 | 1.3 | -0.02em | Page title "VPN MONITOR" |
| Card title | 19px | 700 | 1.3 | -0.01em | Card h2 headings |
| Server name | 16px | 600 | 1.4 | 0 | Server card titles |
| Body/default | 14px | 400 | 1.5 | 0 | Form labels, descriptions |
| Body/small | 13px | 400 | 1.5 | 0 | Secondary info |
| Caption | 12px | 500 | 1.4 | 0.02em | Metadata, hints |
| Overline | 12px | 600 | 1.3 | 0.01em | Subtitle "vless · xhttp · reality" |
| Mono/data | 11.5px | 500 | 1.4 | 0 | Server meta, timestamps |

### Font Stack
- Primary: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro Display', 'Helvetica Neue', system-ui, sans-serif
- Mono: ui-monospace, 'SF Mono', 'Menlo', monospace

### Rules
- 2 font families max (system sans + system mono)
- Body text never below 12px
- Mono font for all data/timestamps/technical values
- Tabular numbers for stats (font-variant-numeric: tabular-nums)

## 4. Spacing & Layout

### Base Unit
All spacing derives from a base of **4px**.

| Token | Value | Usage |
|-------|-------|-------|
| --space-1 | 4px | Tight gaps |
| --space-2 | 8px | Compact groups |
| --space-3 | 12px | Default inline gaps |
| --space-4 | 16px | Card inner padding |
| --space-5 | 20px | Card padding |
| --space-6 | 24px | Section gaps |
| --space-8 | 32px | Page padding |
| --space-10 | 40px | Large section gaps |
| --space-12 | 48px | Hero spacing |

### Grid
- Max content width: 1080px
- Page padding: 32px horizontal, 70px bottom
- Breakpoints: mobile ≤768px (single column), desktop >768px (multi-column)
- Stats grid: 4 columns desktop, 2 columns mobile
- Server list: auto-fill minmax(300px, 1fr)
- Media grid: 2 columns desktop, 1 column mobile
- Form grid: 2 columns desktop, 1 column mobile

### Rules
- Container max-width: 1080px with auto margins
- Cards use 24-28px padding
- Border radius: 20-26px for cards, 14px for inputs, 100px for pills/buttons
- Consistent vertical rhythm: 22-30px between cards

## 5. Components

### Glass Card
- **Structure**: div.card with backdrop-filter, gradient fill, glass border
- **Variants**: default (glass-fill), flat (glass-fill-flat)
- **Spacing**: padding 28px 30px, border-radius 26px
- **States**: default, hover (translateY(-2px))
- **Accessibility**: semantic heading hierarchy (h2 inside card)
- **Motion**: fade-in on tab switch

### Stat Box
- **Structure**: div.stat-box with glass fill, centered value + label
- **Variants**: total (blue), up (green), degraded (orange), down (red)
- **Spacing**: padding 22px 16px, border-radius 24px
- **States**: default, hover (translateY(-3px))
- **Accessibility**: color + text label for status
- **Motion**: counter animation on value change

### Server Card
- **Structure**: server-item with status badge, VLESS indicator, history chart
- **Variants**: up, down, degraded, unknown
- **Spacing**: padding 20px 22px, border-radius 20px, min-height 210px
- **States**: default, hover (background shift, translateY(-2px))
- **Accessibility**: status badge text + color
- **Motion**: chart reveal on toggle

### Tab Bar
- **Structure**: pill-shaped container with tab buttons
- **Variants**: active (blue-soft background, blue text), inactive (transparent)
- **Spacing**: padding 6px, gap 8px, border-radius 100px
- **States**: default, active, hover
- **Accessibility**: aria-selected, keyboard navigation
- **Motion**: fade-in on panel switch

### Form Input
- **Structure**: input/select/textarea with glass background
- **Variants**: text, select, textarea
- **Spacing**: padding 12px 16px, border-radius 14px
- **States**: default, focus (blue border, blue-soft shadow)
- **Accessibility**: label association, focus ring
- **Motion**: border-color transition 200ms

### Toggle Switch
- **Structure**: checkbox with custom appearance
- **Variants**: off (gray), on (blue)
- **Spacing**: 48px × 29px
- **States**: default, checked, active (thumb scale)
- **Accessibility**: label association
- **Motion**: thumb slide transition 250ms

### Button
- **Structure**: button with glass fill
- **Variants**: default (blue text), primary (green bg), danger (red text), mute (gray text)
- **Spacing**: padding 12px 26px, border-radius 100px
- **States**: default, hover (background shift), active (scale 0.96)
- **Accessibility**: focus ring, disabled state
- **Motion**: transform transition 150ms

### Modal
- **Structure**: overlay + glass card with header/body
- **Variants**: default
- **Spacing**: max-width 800px, border-radius 28px
- **States**: hidden, visible
- **Accessibility**: focus trap, close on escape
- **Motion**: slideIn animation 300ms

### Alert
- **Structure**: inline notification with icon
- **Variants**: success (green), error (red), info (blue)
- **Spacing**: padding 14px 20px, border-radius 16px
- **States**: default, dismissed
- **Accessibility**: role="alert"
- **Motion**: slideIn animation 300ms

### Media Item
- **Structure**: flex row with badge, info, actions
- **Variants**: normal (blue badge), adult (red badge), source (orange badge)
- **Spacing**: padding 10px 12px, border-radius 14px
- **States**: default
- **Accessibility**: text labels
- **Motion**: none

## 6. Motion & Interaction

### Timing

| Type | Duration | Easing | Usage |
|------|----------|--------|-------|
| Micro | 100-150ms | cubic-bezier(.2,.8,.2,1) | Button press, toggle |
| Standard | 200-300ms | cubic-bezier(.2,.8,.2,1) | Panel open, tab switch |
| Emphasis | 400-600ms | cubic-bezier(.2,.8,.2,1) | Page load, hero entry |
| Wallpaper | 22-34s | ease-in-out infinite | Mesh blob animation |

### Rules
- Only animate transform, opacity, filter
- GPU-composited animation only
- Respect prefers-reduced-motion: disable wallpaper, pulse, alerts
- Hover states on all interactive elements
- Active states (scale 0.92-0.98) on buttons
- Focus rings on all focusable elements

## 7. Depth & Surface

### Strategy: Glassmorphism (mixed — backdrop-filter + borders + gradients)

| Level | Value | Usage |
|-------|-------|-------|
| Base layer | #05070D solid | Page background |
| Mesh layer | 4 animated blobs with blur(110px) | Background decoration |
| Mesh overlay | rgba(5,7,13,0.6) | Tint over mesh |
| Glass surface | backdrop-filter: blur(30px) saturate(180%) + gradient fill | Cards, modals |
| Glass border | 1px solid rgba(255,255,255,0.16) | Card edges |
| Inner highlight | linear-gradient top 1px transparent→white→transparent | Card top edge sheen |
| Shadow | 0 10px 40px rgba(0,0,0,0.3) | Card depth |

### Rules
- Every glass surface has: gradient fill + border + backdrop-filter
- Inner highlight (pseudo-element) on cards for edge refraction
- Shadows tinted to background (dark, not pure black)
- No flat surfaces — everything has depth through transparency

## 8. Accessibility Constraints & Accepted Debt

### Constraints
- WCAG 2.2 AA target
- Contrast floor: 4.5:1 body, 3:1 large text
- Visible focus on every interactive element
- Full keyboard reachability (tabs, forms, modals)
- prefers-reduced-motion respected (wallpaper, pulse, alerts disabled)
- Semantic HTML: nav, main, section, button, label
- Form inputs properly labeled

### Accepted Debt

| Item | Location | Why accepted | Owner / Exit |
|------|----------|--------------|--------------|
| Inline styles in HTML | index.html | Single-file architecture, no build step | Future: extract to CSS classes |
| No skip-to-content link | index.html | Admin dashboard, not public site | Add if accessibility audit requires |
| No custom 404 page | Router-hosted | Served from router, no routing | N/A |
| Emoji in tab labels | Tab bar | Russian-speaking admin audience, visual clarity | Replace with SVG icons if needed |
