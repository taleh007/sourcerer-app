package app.hashers

import app.utils.RepoHelper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.io.File
import java.io.IOException
import java.util.*

object TestPerfomanceHasher {
    val path = "../gecko-dev/.git"

    var start = 0L
    var end = 0L

    var commitsInfo: LinkedList<Pair<String, String>> = LinkedList()

    fun test() {
        val git = loadGit(path)
        testSimpleIteration(git, iterateRepo, "Simple Iteration")
        testSimpleIteration(git, iterateRepo, "Simple Iteration repeat")
        testSimpleIteration(git, iterateRepoWithLoop,
                            "Simple Iteration with loop")
        testSimpleIteration(git, iterateRepoWithDispose,
                            "Simple Iteration with dispose")
        testSimpleIteration(git, iterateRepoWithoutBody,
                            "Simple Iteration without body")
        testSimpleIteration(git, iterateRepoSaveCommitsInfo,
                            "Simple Iteration save commit info")
    }

    private fun testSimpleIteration(git: Git, func: (git: Git) -> Unit,
                                    name: String) {
        println("> Testing $name...")
        val start = System.nanoTime()
        func(git)
        val end = System.nanoTime()
        val secondsElapsed = (end - start) / 1000000000
        println("< Testing finished. Time elapsed: $secondsElapsed seconds")
        val free = Runtime.getRuntime().freeMemory() / (1024 * 1024)
        val total = Runtime.getRuntime().totalMemory() / (1024 * 1024)
        println("< Memory usage: ${total-free}/$total MB")
        println()
    }

    private val iterateRepo: (Git) -> Unit = {
        val head: RevCommit = RevWalk(it.repository)
            .parseCommit(it.repository.resolve(RepoHelper.MASTER_BRANCH))

        val revWalk = RevWalk(it.repository)
        revWalk.markStart(head)

        revWalk.last()
    }

    private val iterateRepoWithLoop: (Git) -> Unit = {
        val head: RevCommit = RevWalk(it.repository)
            .parseCommit(it.repository.resolve(RepoHelper.MASTER_BRANCH))

        val revWalk = RevWalk(it.repository)
        revWalk.markStart(head)

        var commit: RevCommit? = revWalk.next()
        while (commit != null) {
            commit = revWalk.next()
        }
    }

    private val iterateRepoWithoutBody: (Git) -> Unit = {
        val head: RevCommit = RevWalk(it.repository)
            .parseCommit(it.repository.resolve(RepoHelper.MASTER_BRANCH))

        val revWalk = RevWalk(it.repository)
        revWalk.markStart(head)
        revWalk.isRetainBody = false

        var commit: RevCommit? = revWalk.next()
        while (commit != null) {
            commit = revWalk.next()
        }
    }

    private val iterateRepoWithDispose: (Git) -> Unit = {
        val head: RevCommit = RevWalk(it.repository)
            .parseCommit(it.repository.resolve(RepoHelper.MASTER_BRANCH))

        val revWalk = RevWalk(it.repository)
        revWalk.markStart(head)

        var commit: RevCommit? = revWalk.next()
        while (commit != null) {
            commit.disposeBody()
            commit = revWalk.next()
        }
        revWalk.dispose()
    }

    private val iterateRepoSaveCommitsInfo: (Git) -> Unit = {
        val head: RevCommit = RevWalk(it.repository)
            .parseCommit(it.repository.resolve(RepoHelper.MASTER_BRANCH))

        val revWalk = RevWalk(it.repository)
        revWalk.markStart(head)

        var commit: RevCommit? = revWalk.next()
        while (commit != null) {
            commitsInfo.add(Pair(commit.name, commit.authorIdent.emailAddress))
            commit.disposeBody()
            commit = revWalk.next()
        }
        revWalk.dispose()
    }

    private fun loadGit(path: String): Git {
        return try {
            Git.open(File(path))
        } catch (e: IOException) {
            throw IllegalStateException("Cannot access repository at $path")
        }
    }
}
