package game

import gdext.api.Gd
import gdext.classes.Node2D

case class HelloWorld(
  var speed: Double
) extends Node2D:
  override def _ready(): Unit = 
    Gd.print(s"Hello from scala! (speed=$speed)")
