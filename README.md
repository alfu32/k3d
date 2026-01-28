![appicon.png](assets/appicon.png)
# K3D



![img_1.png](docs/img_1.png)


K3D is a Kotlin + libGDX (jvm) desktop application for interactively creating and editing simple 3D geometry. It focuses on direct modeling with edges and planar faces, real-time snapping, and tool-driven workflows.

![icons.png](assets/icons.png)

## What it does today

- Renders a 3D viewport with grid, axes, and guides.
- Supports camera orbit and pan controls.
- Provides core drawing tools for lines, rectangles, and circles.
- Creates faces from closed polylines and from rectangle/circle tools.
- Includes snapping to endpoints, midpoints, grid points/lines, and guides.
- Offers push/pull extrusion on planar faces.
- Provides a cleanup action to split intersections and re-weld edges.

## What it is intended to do

- Expand modeling tools (move/rotate/scale, selection, painting, erasing).
- Build a robust topology layer for edges/faces with adjacency.
- Support face splitting and more reliable boolean-like operations.
- Improve performance through spatial indices for picking and snapping.
- Add project persistence (save/load) and export formats.

## Status

This is an active work-in-progress. APIs and behavior may change as modeling and topology features evolve.
![img_16.png](docs/img_16.png)
![img_17.png](docs/img_17.png)
![img_18.png](docs/img_18.png)
![img_19.png](docs/img_19.png)
