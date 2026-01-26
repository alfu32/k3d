1. i want the grid size to be accessible for parametrization to the user in the settings panel, this measure will be used also by all the helpers ( t - the axis system and g - the grid planes system ) if
   it isnt already used.
2. i would also like to be able to toggle what the 3d renderer renders ( toggle the visibility of each of the entities types ). i dont know in which panel we should put these ... propose something
   ( maybe the selection/entities panel )
3. i want the cli to enter a repl mode after launching edit, it should accept commands towards tools like
```
line 0,0,0 1,2,3
camera target 0,0,0
camera position 10,10,8
select 3,2,7 9,5,9
list objects
list lines
list faces
save
exit
show <panel name>
hide <panel name>
list panels
... etc
```
well in principle we could have each internal tool and each plugin, the app model return a list of named functions that take parameters.
