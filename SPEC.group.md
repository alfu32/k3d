## Group Entity

the group is a grouping of lines, faces and other groups.
To create a group the user can press ctrl-g, to ungroup ( explode a group) the user presses ctrl-shif-g.
Groups can have a name which will be available for editing when the group is selected in a special panel 'group' that will show up when a single group is selected and during the edit mode of the group.
when the group is in the edit mode this panel will display a message like ( open when in edit mode and closed when not in edit mode )
When in edit mode the user can only operate on entities inside the opened group, the other moel entities should be left unaffected).
a group should have a special boolean property that will regulate whether the group will be glued to a surface or not when moved or copied. when this property is on the group will be automatically
aligned on the support surface ( so its external normal(0z axis) will be parallel with the support surface's normal. this parameter will also be available in the group parameters panel.
snapping should happen on points from a close group too.

1. we need to separate the __group instance__ anchor, group rotation vectors and group size from the __group definition__ anchor, rotation and size of the group defintion.
when move, rotate or scale a group its definition shouldn't change, only its local instance matrix.
2. gluing onto a surface is weird ( we might have flipped one of the coordinates (maybe z ?)
3. the group properties panel is not working properly: when i select another object the mane in the name box doesn't seem to change to reflect the selection of the other groupm same goes for the checkbox.
   these are group properties ... the panel should reflect the selection change.
   are we sure the renaming of the group actually propagates to the group itself ? same doubt in the checkbox.

the group glueing to surface should not be wrong

the frame should have been laying on the ground.
also a minor remark:
when creating a group the definition anchor should be at min(x,y,z).
when copying one however we shouldn't modify neither of the initial/internal definition vectors, but we should modify it accordingly.
so basically we'll not redefine the group definition only modify the group instance matrix.
this should also mean that the bounding box will appear rotated when rotation is involved ....
