package godothoccon

import java.io.File

/** Config-directory hot-reload.
  *
  * The Rust original used the `notify` crate to get OS file-change events
  * pushed into an mpsc channel, draining them once per frame. Scala Native has
  * no `notify` equivalent, so this port polls instead: each [[pollAndApply]]
  * call re-scans the watched directory's newest modification time and runs the
  * reload callback when it has advanced (coalescing a burst of edits into a
  * single reload, exactly as the original did).
  *
  * Call [[pollAndApply]] from the consuming node's `_process`. It self-throttles
  * to at most one filesystem scan per [[ConfigWatcher.pollIntervalMs]], so
  * calling it every frame is cheap — a full-tree `File.lastModified` walk every
  * frame would otherwise churn the (Zone-backed) allocator needlessly.
  *
  * Ported from `framework/src/config/watch.rs`.
  */
final class ConfigWatcher private (root: File, onChange: () => Unit):
  private var lastMtime: Long = ConfigWatcher.newestMtime(root)
  private var nextScanAt: Long = System.currentTimeMillis() + ConfigWatcher.pollIntervalMs

  /** Re-scan the watched tree; if anything changed since the last poll, run the
    * reload callback once. Throttled to [[ConfigWatcher.pollIntervalMs]] so it is
    * safe to call from `_process` every frame.
    */
  def pollAndApply(): Unit =
    val now = System.currentTimeMillis()
    if now >= nextScanAt then
      nextScanAt = now + ConfigWatcher.pollIntervalMs
      val current = ConfigWatcher.newestMtime(root)
      if current > lastMtime then
        lastMtime = current
        onChange()

object ConfigWatcher:

  /** Minimum wall-clock interval between filesystem scans (ms). Hot-reload
    * latency is bounded by this; 250ms is well below human edit-to-see latency
    * while keeping the per-frame cost negligible. */
  val pollIntervalMs: Long = 250L

  /** Create a watcher rooted at the located config directory. */
  def create(onChange: () => Unit): Either[String, ConfigWatcher] =
    Loader.findConfigDirectory().map(root => new ConfigWatcher(root, onChange))

  /** Create a watcher rooted at an explicit directory. */
  def at(root: File, onChange: () => Unit): ConfigWatcher =
    new ConfigWatcher(root, onChange)

  /** Newest `lastModified` across the directory tree (recursive). */
  private def newestMtime(file: File): Long =
    if file.isDirectory then
      Option(file.listFiles()) match
        case Some(children) => children.foldLeft(file.lastModified())((m, c) => math.max(m, newestMtime(c)))
        case None => file.lastModified()
    else file.lastModified()
