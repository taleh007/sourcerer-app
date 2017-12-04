// Copyright 2017 Sourcerer Inc. All Rights Reserved.
// Author: Anatoly Kislov (anatoly@sourcerer.io)

package app.hashers

import app.Logger
import app.model.Commit
import app.model.DiffContent
import app.model.DiffFile
import app.model.DiffRange
import app.model.Repo
import app.utils.RepoHelper
import io.reactivex.Observable
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.util.io.DisabledOutputStream

/**
* Iterates over the diffs between commits in the repo's history.
*/
object CommitCrawler {
    fun getJGitObservable(git: Git, tail : RevCommit? = null) :
        Observable<Pair<RevCommit, List<Pair<DiffEntry, EditList>>>> =
        Observable.create { subscriber ->

        val repo: Repository = git.repository
        val revWalk = RevWalk(repo)
        val head: RevCommit =
            try { revWalk.parseCommit(repo.resolve(RepoHelper.MASTER_BRANCH)) }
            catch(e: Exception) { throw Exception("No branch") }

        val df = DiffFormatter(DisabledOutputStream.INSTANCE)
        df.setRepository(repo)
        df.setDetectRenames(true)

        revWalk.markStart(head)
        var commit: RevCommit? = revWalk.next()  // Move the walker to the head.
        while (commit != null && commit != tail) {
            val parentCommit: RevCommit? = revWalk.next()

            // Smart casts are not yet supported for a mutable variable captured
            // in an inline lambda, see
            // https://youtrack.jetbrains.com/issue/KT-7186.
            if (Logger.isDebug) {
                val commitName = commit.getName()
                val commitMsg = commit.getShortMessage()
                Logger.debug { "commit: $commitName; '$commitMsg'" }
                if (parentCommit != null) {
                    val parentCommitName = parentCommit.getName()
                    val parentCommitMsg = parentCommit.getShortMessage()
                    Logger.debug {
                        "parent commit: ${parentCommitName}; '${parentCommitMsg}'"
                    }
                }
                else {
                    Logger.debug { "parent commit: null" }
                }
            }

            val diffEntries = df.scan(parentCommit, commit);
            val diffEdits = diffEntries.map { diff ->
                Pair(diff, df.toFileHeader(diff).toEditList())
            }
            subscriber.onNext(Pair(commit, diffEdits))
            commit = parentCommit
        }

        subscriber.onComplete()
    }

    fun getObservable(git: Git,
                      jgitObservable: Observable<Pair<RevCommit, List<Pair<DiffEntry, EditList>>>>,
                      repo: Repo,
                      numCommits: Int = 0): Observable<Commit> {
        var curNumCommits = 0

        return Observable.create<Commit> { subscriber ->
            jgitObservable.subscribe( { (jgitCommit, jgitDiffs) ->
                curNumCommits++

                if (Logger.isDebug) {
                    val perc = if (numCommits != 0) {
                        (curNumCommits.toDouble() / numCommits) * 100
                    } else 0.0
                    Logger.printCommit(jgitCommit.shortMessage, jgitCommit.name, perc)
                }

                // Mapping and stats extraction.
                val commit = Commit(jgitCommit);
                commit.diffs = getDiffFiles(git.repository, jgitDiffs)

                // Count lines on all non-binary files. This is additional
                // statistics to CommitStats because not all file extensions
                // may be supported.
                commit.numLinesAdded = commit.diffs.fold(0) { total, file ->
                    total + file.getAllAdded().size
                }
                commit.numLinesDeleted = commit.diffs.fold(0) { total, file ->
                    total + file.getAllDeleted().size
                }
                commit.repo = repo

                subscriber.onNext(commit)
            })
            subscriber.onComplete()
        }
    }

    fun getObservable(git: Git,
                      repo: Repo,
                      numCommits: Int = 0): Observable<Commit> {
        return getObservable(git, getJGitObservable(git), repo, numCommits)
    }

    private fun getDiffFiles(jgitRepo: Repository,
                             jgitDiffs: List<Pair<DiffEntry, EditList>>) : List<DiffFile> {
        return jgitDiffs
            // Skip binary files.
            .filter { (diff, _) ->
                val fileId =
                    if (diff.getNewPath() != DiffEntry.DEV_NULL) {
                        diff.getNewId().toObjectId()
                    } else {
                        diff.getOldId().toObjectId()
                    }
                val stream = try { jgitRepo.open(fileId).openStream() }
                catch (e: Exception) { null }
                stream != null && !RawText.isBinary(stream)
            }
            .map { (diff, edits) ->
                // TODO(anatoly): Can produce exception for large object.
                // Investigate for size.
                val new = try {
                    getContentByObjectId(jgitRepo, diff.newId.toObjectId())
                } catch (e: Exception) {
                    Logger.error(e)
                    null
                }
                val old = try {
                    getContentByObjectId(jgitRepo, diff.oldId.toObjectId())
                } catch (e: Exception) {
                    Logger.error(e)
                    null
                }

                val diffFiles = mutableListOf<DiffFile>()
                if (new != null && old != null) {
                    val path = when (diff.changeType) {
                        DiffEntry.ChangeType.DELETE -> diff.oldPath
                        else -> diff.newPath
                    }
                    diffFiles.add(DiffFile(path = path,
                        changeType = diff.changeType,
                        old = DiffContent(old, edits.map { edit ->
                            DiffRange(edit.beginA, edit.endA)
                        }),
                        new = DiffContent(new, edits.map { edit ->
                            DiffRange(edit.beginB, edit.endB)
                        })
                    ))
                }
                diffFiles
            }
            .flatten()
    }

    private fun getContentByObjectId(repo: Repository,
                                     objectId: ObjectId): List<String> {
        return try {
            val obj = repo.open(objectId)
            val rawText = RawText(obj.bytes)
            val content = ArrayList<String>(rawText.size())
            for (i in 0..(rawText.size() - 1)) {
                content.add(rawText.getString(i))
            }
            return content
        } catch (e: Exception) {
            listOf()
        }
    }
}
