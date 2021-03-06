// Copyright 2017 Sourcerer Inc. All Rights Reserved.
// Author: Anatoly Kislov (anatoly@sourcerer.io)

package app.ui

import app.Logger
import app.api.Api
import app.config.Configurator
import app.model.LocalRepo
import app.utils.RepoHelper
import app.utils.UiHelper

/**
 * Add repository dialog console UI state.
 */
class AddRepoState constructor(private val context: Context,
                               private val api: Api,
                               private val configurator: Configurator)
    : ConsoleState {
    override fun doAction() {
        if (configurator.getLocalRepos().isNotEmpty()) return

        while (true) {
            Logger.print("Type a path to repository, or hit Enter to continue.")
            val pathString = readLine() ?: ""

            if (pathString.isEmpty()) {
                if (configurator.getLocalRepos().isEmpty()) {
                    Logger.print("Add at least one valid repository.")
                } else {
                    break // User finished to add repos.
                }
            } else {
                if (RepoHelper.isValidRepo(pathString)) {
                    Logger.print("Added git repository at $pathString.")
                    val localRepo = LocalRepo(pathString)
                    localRepo.hashAllContributors = UiHelper.confirm("Do you "
                        + "want to hash commits of all contributors?",
                        defaultIsYes = true)
                    configurator.addLocalRepoPersistent(localRepo)
                    configurator.saveToFile()
                } else {
                    Logger.print("Directory should contain a valid git " +
                        "repository.")
                    Logger.print("Make sure that master branch with at least " +
                        "one commit exists.")
                }
            }
        }

        Logger.info(Logger.Events.CONFIG_SETUP) { "Config setup" }
    }

    override fun next() {
        context.changeState(EmailState(context, api, configurator))
    }
}
