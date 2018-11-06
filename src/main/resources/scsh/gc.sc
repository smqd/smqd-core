/*
  @Begin
    Runs the garbage collector.
    Calling this method suggests that the Java virtual machine expend effort
    toward recycling unused objects in order to make the memory they currently
    occupy available for quick reuse. When control returns from the method call,
    the virtual machine has made its best effort to recycle all discarded objects.
    The name gc stands for "garbage collector".
    The virtual machine performs this recycling process automatically as needed,
    in a separate thread, even if the gc method is not invoked explicitly.

  Syntax
    gc
  @End
 */

System.gc()