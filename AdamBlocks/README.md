# Adam Blocks

Client-side Fabric mod for Minecraft 1.21.11. Draws real 3D boxes around the
blocks you choose, visible through walls, with optional lines pointing at them
and a hover tooltip that names the block or lists grouped counts.

## Use
- Open the menu with Right Shift, or from Mod Menu (Adam Blocks > config button).
- Press "+ Add block", search, and click any block to track it.
- Per block: On/Off, Edit, x to remove.
- Edit: red/green/blue sliders or quick color buttons, fill opacity (0 = outline
  only), and "Tracer to this block" On/Off.
- Settings: Tracers On/Off (all lines), Tooltip On/Off, range, group distance
  (how close blocks must be to merge in the tooltip), menu key.

## Build
Push to GitHub, open the Actions tab, run "build", and download the jar from
the artifact. If a dependency version fails to resolve, copy the version lines
from your working Adam-freecam gradle.properties.

Config file: config/adamblocks.json
