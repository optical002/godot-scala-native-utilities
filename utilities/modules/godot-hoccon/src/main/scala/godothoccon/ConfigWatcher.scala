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
  // Snapshot the set of files ONCE. The config tree is static at runtime (only
  // file *contents* change, not which files exist), so we never re-walk the
  // directory while polling. Re-running `File.listFiles()` recursively every
  // poll churned the Scala-Native (Zone-backed) allocator and could corrupt the
  // returned array's heap layout — observed as random `MatchError` /
  // array-out-of-bounds crashes a few seconds into play, surfacing through the
  // failing `_process` poll. Stat-ing the cached `File`s avoids all of that.
  private val files: Array[File] = ConfigWatcher.collectFiles(root)
  private var lastMtime: Long = newestMtime()
  private var nextScanAt: Long = System.currentTimeMillis() + ConfigWatcher.pollIntervalMs

  /** Re-stat the (cached) config files; if any changed since the last poll, run
    * the reload callback once. Throttled to [[ConfigWatcher.pollIntervalMs]], so
    * it is safe (and cheap) to call from `_process` every frame.
    */
  def pollAndApply(): Unit =
    val now = System.currentTimeMillis()
    if now >= nextScanAt then
      nextScanAt = now + ConfigWatcher.pollIntervalMs
      val current = newestMtime()
      if current > lastMtime then
        lastMtime = current
        onChange()

  /** Newest `lastModified` across the cached file set (no directory walk). */
  private def newestMtime(): Long =
    var newest = 0L
    var i = 0
    while i < files.length do
      val m = files(i).lastModified()
      if m > newest then newest = m
      i += 1
    newest

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

  /** Flatten the directory tree to a list of files, walked ONCE at construction
    * (see the note in [[ConfigWatcher]] on why we don't re-walk while polling). */
  private def collectFiles(root: File): Array[File] =
    val acc = scala.collection.mutable.ArrayBuffer.empty[File]
    def walk(file: File): Unit =
      if file.isDirectory then
        val children = file.listFiles()
        if children != null then
          var i = 0
          while i < children.length do
            walk(children(i))
            i += 1
      else acc += file
    walk(root)
    acc.toArray
