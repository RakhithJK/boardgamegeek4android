package com.boardgamegeek.db

import android.content.ContentProviderOperation
import android.net.Uri
import androidx.lifecycle.LiveData
import com.boardgamegeek.BggApplication
import com.boardgamegeek.auth.AccountUtils
import com.boardgamegeek.entities.LocationEntity
import com.boardgamegeek.entities.PlayEntity
import com.boardgamegeek.entities.PlayerColorEntity
import com.boardgamegeek.entities.PlayerEntity
import com.boardgamegeek.extensions.*
import com.boardgamegeek.livedata.AbsentLiveData
import com.boardgamegeek.livedata.RegisteredLiveData
import com.boardgamegeek.provider.BggContract
import com.boardgamegeek.provider.BggContract.PlayPlayers
import com.boardgamegeek.provider.BggContract.Plays
import timber.log.Timber

class PlayDao(private val context: BggApplication) {
    fun deleteUnupdatedPlays(gameId: Int, since: Long) {
        val count = context.contentResolver.delete(Plays.CONTENT_URI,
                "${Plays.SYNC_TIMESTAMP}<? AND ${Plays.OBJECT_ID}=? AND ${Plays.UPDATE_TIMESTAMP.whereZeroOrNull()} AND ${Plays.DELETE_TIMESTAMP.whereZeroOrNull()} AND ${Plays.DIRTY_TIMESTAMP.whereZeroOrNull()}",
                arrayOf(since.toString(), gameId.toString()))
        Timber.i("Deleted %,d unupdated play(s) of game ID=%s", count, gameId)
    }

    fun countPlays(): LiveData<Int> {
        val uri = Plays.CONTENT_SIMPLE_URI
        val selection = createPlaySelectionAndArgs()
        var count = 0
        return RegisteredLiveData(context, uri, true) {
            context.contentResolver.load(uri,
                    arrayOf(Plays.SUM_QUANTITY),
                    selection.first,
                    selection.second
            )?.use {
                if (it.moveToFirst()) {
                    count = it.getIntOrNull(0) ?: 0
                }
            }
            return@RegisteredLiveData count
        }
    }

    fun loadPlaysInProgress(): LiveData<List<PlayEntity>> {
        val uri = Plays.CONTENT_URI
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadPlays(uri, createInProgressPlaySelectionAndArgs())
        }
    }

    fun loadPlaysNotInProgress(): LiveData<List<PlayEntity>> {
        val uri = Plays.CONTENT_URI
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadPlays(uri, createUpdatedPlaySelectionAndArgs())
        }
    }

    fun loadPlaysByGame(gameId: Int): LiveData<List<PlayEntity>> {
        if (gameId == BggContract.INVALID_ID) return AbsentLiveData.create()
        val uri = Plays.CONTENT_URI
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadPlays(uri, createGamePlaySelectionAndArgs(gameId))
        }
    }

    private fun loadPlays(uri: Uri, selection: Pair<String, Array<String>>): ArrayList<PlayEntity> {
        val list = arrayListOf<PlayEntity>()
        context.contentResolver.load(uri,
                arrayOf(Plays._ID,
                        Plays.PLAY_ID,
                        Plays.DATE,
                        Plays.OBJECT_ID,
                        Plays.ITEM_NAME,
                        Plays.QUANTITY,
                        Plays.LENGTH,
                        Plays.LOCATION,
                        Plays.INCOMPLETE,
                        Plays.NO_WIN_STATS,
                        Plays.COMMENTS,
                        Plays.SYNC_TIMESTAMP,
                        Plays.PLAYER_COUNT,
                        Plays.DIRTY_TIMESTAMP,
                        Plays.START_TIME),
                selection.first,
                selection.second
        )?.use {
            if (it.moveToFirst()) {
                do {
                    list.add(PlayEntity(
                            internalId = it.getLong(Plays._ID),
                            playId = it.getInt(Plays.PLAY_ID),
                            date = it.getString(Plays.DATE),
                            gameId = it.getInt(Plays.OBJECT_ID),
                            gameName = it.getString(Plays.ITEM_NAME),
                            quantity = it.getIntOrNull(Plays.QUANTITY) ?: 1,
                            length = it.getIntOrNull(Plays.LENGTH) ?: 0,
                            location = it.getStringOrEmpty(Plays.LOCATION),
                            incomplete = it.getInt(Plays.INCOMPLETE) == 1,
                            noWinStats = it.getInt(Plays.NO_WIN_STATS) == 1,
                            comments = it.getStringOrEmpty(Plays.COMMENTS),
                            syncTimestamp = it.getLong(Plays.SYNC_TIMESTAMP),
                            playerCount = it.getInt(Plays.PLAYER_COUNT),
                            dirtyTimestamp = it.getLong(Plays.DIRTY_TIMESTAMP),
                            startTime = it.getLong(Plays.START_TIME)
                    ))
                } while (it.moveToNext())
            }
        }
        return list
    }

    private fun createPlaySelectionAndArgs() =
            Plays.DIRTY_TIMESTAMP.whereZeroOrNull() to emptyArray<String>()

    private fun createInProgressPlaySelectionAndArgs() =
            "${Plays.DIRTY_TIMESTAMP}>0" to emptyArray<String>()

    private fun createUpdatedPlaySelectionAndArgs() =
            "${Plays.DIRTY_TIMESTAMP.whereZeroOrNull()} AND ${Plays.DELETE_TIMESTAMP.whereZeroOrNull()}" to emptyArray<String>()

    private fun createGamePlaySelectionAndArgs(gameId: Int) =
            "${Plays.OBJECT_ID}=? AND ${Plays.DELETE_TIMESTAMP.whereZeroOrNull()}" to arrayOf(gameId.toString())

    fun loadPlayers(includeIncompletePlays: Boolean): List<PlayerEntity> {
        val selection = arrayListOf<String>().apply {
            add(Plays.DELETE_TIMESTAMP.whereZeroOrNull())
            if (!AccountUtils.getUsername(context).isNullOrBlank()) {
                add(BggContract.PlayPlayers.USER_NAME + "!=?")
            }
            if (!includeIncompletePlays) {
                add(Plays.INCOMPLETE.whereZeroOrNull())
            }
        }.joinTo(" AND ").toString()
        val selectionArgs = AccountUtils.getUsername(context)?.let { username ->
            when {
                username.isBlank() -> null
                else -> arrayOf(username)
            }
        }
        val results = arrayListOf<PlayerEntity>()
        context.contentResolver.load(
                Plays.buildPlayersByUniquePlayerUri(),
                arrayOf(
                        PlayPlayers._ID,
                        PlayPlayers.NAME,
                        PlayPlayers.USER_NAME,
                        PlayPlayers.SUM_QUANTITY,
                        PlayPlayers.SUM_WINS),
                selection,
                selectionArgs,
                "${PlayPlayers.SUM_QUANTITY.descending()}, ${PlayPlayers.NAME.collateNoCase()}"
        )?.use {
            if (it.moveToFirst()) {
                do {
                    results += PlayerEntity(
                            it.getLongOrNull(PlayPlayers._ID) ?: BggContract.INVALID_ID.toLong(),
                            it.getStringOrEmpty(PlayPlayers.NAME),
                            it.getStringOrEmpty(PlayPlayers.USER_NAME),
                            it.getIntOrZero(PlayPlayers.SUM_QUANTITY),
                            it.getIntOrZero(PlayPlayers.SUM_WINS)
                    )
                } while (it.moveToNext())
            }
        }
        return results
    }

    fun loadPlayersAsLiveData(includeIncompletePlays: Boolean): LiveData<List<PlayerEntity>> {
        return RegisteredLiveData(context, Plays.buildPlayersByUniquePlayerUri(), true) {
            return@RegisteredLiveData loadPlayers(includeIncompletePlays)
        }
    }

    fun loadUserPlayerAsLiveData(username: String): LiveData<PlayerEntity> {
        val uri = Plays.buildPlayersByUniqueUserUri()
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadPlayer(
                    uri,
                    "${PlayPlayers.USER_NAME}=? AND ${Plays.NO_WIN_STATS.whereZeroOrNull()}",
                    arrayOf(username))
        }
    }

    fun loadNonUserPlayerAsLiveData(playerName: String): LiveData<PlayerEntity> {
        val uri = Plays.buildPlayersByUniquePlayerUri()
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadPlayer(
                    uri,
                    "${PlayPlayers.USER_NAME.whereEqualsOrNull()} AND play_players.${PlayPlayers.NAME}=?",
                    arrayOf("", playerName))
        }
    }

    private fun loadPlayer(uri: Uri, selection: String, selectionArgs: Array<String>): PlayerEntity? {
        context.contentResolver.load(
                uri,
                arrayOf(
                        PlayPlayers._ID,
                        PlayPlayers.NAME,
                        PlayPlayers.USER_NAME,
                        PlayPlayers.SUM_QUANTITY,
                        PlayPlayers.SUM_WINS
                ),
                selection,
                selectionArgs
        )?.use {
            return if (it.moveToFirst()) {
                PlayerEntity(
                        it.getLongOrNull(PlayPlayers._ID) ?: BggContract.INVALID_ID.toLong(),
                        it.getStringOrEmpty(PlayPlayers.NAME),
                        it.getStringOrEmpty(PlayPlayers.USER_NAME),
                        it.getIntOrZero(PlayPlayers.SUM_QUANTITY),
                        it.getIntOrZero(PlayPlayers.SUM_WINS)
                )
            } else null
        }
        return null
    }

    fun loadPlayerColors(playerName: String): LiveData<List<PlayerColorEntity>> {
        val uri = BggContract.PlayerColors.buildPlayerUri(playerName)
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadColors(uri)
        }
    }

    fun loadUserColors(username: String): LiveData<List<PlayerColorEntity>> {
        val uri = BggContract.PlayerColors.buildUserUri(username)
        return RegisteredLiveData(context, uri, true) {
            return@RegisteredLiveData loadColors(uri)
        }
    }

    private fun loadColors(uri: Uri): List<PlayerColorEntity> {
        val results = arrayListOf<PlayerColorEntity>()
        context.contentResolver.load(
                uri,
                arrayOf(
                        BggContract.PlayerColors._ID,
                        BggContract.PlayerColors.PLAYER_COLOR,
                        BggContract.PlayerColors.PLAYER_COLOR_SORT_ORDER
                )
        )?.use {
            if (it.moveToFirst()) {
                do {
                    results += PlayerColorEntity(
                            it.getStringOrEmpty(BggContract.PlayerColors.PLAYER_COLOR),
                            it.getIntOrNull(BggContract.PlayerColors.PLAYER_COLOR_SORT_ORDER) ?: 0
                    )
                } while (it.moveToNext())
            }
        }
        return results
    }

    enum class LocationSortBy {
        NAME, PLAY_COUNT
    }

    fun loadLocationsAsLiveData(sortBy: LocationSortBy = LocationSortBy.NAME): LiveData<List<LocationEntity>> {
        return RegisteredLiveData(context, Plays.buildLocationsUri(), true) {
            return@RegisteredLiveData loadLocations(sortBy)
        }
    }

    private fun loadLocations(sortBy: LocationSortBy = LocationSortBy.NAME): List<LocationEntity> {
        val results = arrayListOf<LocationEntity>()
        val sortOrder = when (sortBy) {
            LocationSortBy.NAME -> ""
            LocationSortBy.PLAY_COUNT -> Plays.SUM_QUANTITY.descending()
        }
        context.contentResolver.load(
                Plays.buildLocationsUri(),
                arrayOf(
                        Plays._ID,
                        Plays.LOCATION,
                        Plays.SUM_QUANTITY
                ),
                sortOrder = sortOrder
        )?.use {
            if (it.moveToFirst()) {
                do {
                    results += LocationEntity(
                            it.getStringOrEmpty(BggContract.Plays.LOCATION),
                            it.getIntOrNull(BggContract.Plays.SUM_QUANTITY) ?: 0
                    )
                } while (it.moveToNext())
            }
        }
        return results
    }

    enum class PlayerSortBy {
        NAME, PLAY_COUNT, WIN_COUNT
    }

    fun loadPlayersAsLiveData(sortBy: PlayerSortBy = PlayerSortBy.NAME): LiveData<List<PlayerEntity>> {
        return RegisteredLiveData(context, Plays.buildPlayersByUniquePlayerUri(), true) {
            return@RegisteredLiveData loadPlayers(sortBy)
        }
    }

    private fun loadPlayers(sortBy: PlayerSortBy = PlayerSortBy.NAME): List<PlayerEntity> {
        val results = arrayListOf<PlayerEntity>()
        val sortOrder = when (sortBy) {
            PlayerSortBy.NAME -> PlayPlayers.NAME.collateNoCase()
            PlayerSortBy.PLAY_COUNT -> Plays.SUM_QUANTITY.descending()
            PlayerSortBy.WIN_COUNT -> Plays.SUM_WINS.descending()
        }
        context.contentResolver.load(
                Plays.buildPlayersByUniquePlayerUri(),
                arrayOf(
                        PlayPlayers._ID,
                        PlayPlayers.NAME,
                        PlayPlayers.USER_NAME,
                        PlayPlayers.SUM_QUANTITY,
                        PlayPlayers.SUM_WINS
                ),
                sortOrder = sortOrder
        )?.use {
            if (it.moveToFirst()) {
                do {
                    results += PlayerEntity(
                            it.getLongOrNull(BggContract.PlayPlayers._ID) ?: BggContract.INVALID_ID.toLong(),
                            it.getStringOrEmpty(BggContract.PlayPlayers.NAME),
                            it.getStringOrEmpty(BggContract.PlayPlayers.USER_NAME),
                            it.getIntOrNull(BggContract.PlayPlayers.SUM_QUANTITY) ?: 0,
                            it.getIntOrNull(BggContract.PlayPlayers.SUM_WINS) ?: 0
                    )
                } while (it.moveToNext())
            }
        }
        return results
    }

    fun createCopyPlayerColorsOperations(oldName: String, newName: String): ArrayList<ContentProviderOperation> {
        val colors = loadColors(BggContract.PlayerColors.buildPlayerUri(oldName))
        val batch = arrayListOf<ContentProviderOperation>()
        colors.forEach {
            batch.add(ContentProviderOperation
                    .newInsert(BggContract.PlayerColors.buildPlayerUri(newName))
                    .withValue(BggContract.PlayerColors.PLAYER_COLOR, it.description)
                    .withValue(BggContract.PlayerColors.PLAYER_COLOR_SORT_ORDER, it.sortOrder)
                    .build())
        }
        return batch
    }

    fun createCopyPlayerColorsToUserOperations(playerName: String, username: String): ArrayList<ContentProviderOperation> {
        val colors = loadColors(BggContract.PlayerColors.buildPlayerUri(playerName))
        val batch = arrayListOf<ContentProviderOperation>()
        colors.forEach {
            batch.add(ContentProviderOperation
                    .newInsert(BggContract.PlayerColors.buildUserUri(username))
                    .withValue(BggContract.PlayerColors.PLAYER_COLOR, it.description)
                    .withValue(BggContract.PlayerColors.PLAYER_COLOR_SORT_ORDER, it.sortOrder)
                    .build())
        }
        return batch
    }

    fun createDirtyPlaysForUserAndNickNameOperations(username: String, nickName: String, timestamp: Long = System.currentTimeMillis()): ArrayList<ContentProviderOperation> {
        val selection = createNickNameSelectionAndArgs(username, nickName)
        return createDirtyPlaysOperations(selection, timestamp)
    }

    fun createDirtyPlaysForNonUserPlayerOperations(oldName: String, timestamp: Long = System.currentTimeMillis()): ArrayList<ContentProviderOperation> {
        val selection = createNonUserPlayerSelectionAndArgs(oldName)
        return createDirtyPlaysOperations(selection, timestamp)
    }

    private fun createDirtyPlaysOperations(selection: Pair<String, Array<String>>, timestamp: Long): ArrayList<ContentProviderOperation> {
        val internalIds = context.contentResolver.queryLongs(
                Plays.buildPlayersByPlayUri(),
                Plays._ID,
                "(${selection.first}) AND ${Plays.UPDATE_TIMESTAMP.whereZeroOrNull()} AND ${Plays.DELETE_TIMESTAMP.whereZeroOrNull()} AND ${Plays.DIRTY_TIMESTAMP.whereZeroOrNull()}",
                selection.second)
        val batch = arrayListOf<ContentProviderOperation>()
        internalIds.filter { it != BggContract.INVALID_ID.toLong() }.forEach {
            batch += ContentProviderOperation
                    .newUpdate(Plays.buildPlayUri(it))
                    .withValue(Plays.UPDATE_TIMESTAMP, timestamp)
                    .build()
        }
        return batch
    }

    fun createNickNameUpdateOperation(username: String, nickName: String): ContentProviderOperation {
        val selection = createNickNameSelectionAndArgs(username, nickName)
        return ContentProviderOperation
                .newUpdate(Plays.buildPlayersByPlayUri())
                .withSelection(selection.first, selection.second)
                .withValue(PlayPlayers.NAME, nickName)
                .build()
    }

    fun countNickNameUpdatePlays(username: String, nickName: String): Int {
        val selection = createNickNameSelectionAndArgs(username, nickName)
        return context.contentResolver.queryCount(
                Plays.buildPlayersByPlayUri(),
                selection.first,
                selection.second
        )
    }

    /**
     * Change player records from the old name to the new name (username  must be blank)
     */
    fun createRenameUpdateOperation(oldName: String, newName: String): ContentProviderOperation {
        val selection = createNonUserPlayerSelectionAndArgs(oldName)
        return ContentProviderOperation
                .newUpdate(Plays.buildPlayersByPlayUri())
                .withValue(PlayPlayers.NAME, newName)
                .withSelection(selection.first, selection.second)
                .build()
    }

    fun createAddUsernameOperation(playerName: String, username: String): ContentProviderOperation {
        val selection = createNonUserPlayerSelectionAndArgs(playerName)
        return ContentProviderOperation
                .newUpdate(Plays.buildPlayersByPlayUri())
                .withValue(PlayPlayers.USER_NAME, username)
                .withSelection(selection.first, selection.second)
                .build()
    }

    /**
     * Create an operation to delete the colors of the specified player
     */
    fun createDeletePlayerColorsOperation(playerName: String): ContentProviderOperation {
        return ContentProviderOperation.newDelete(BggContract.PlayerColors.buildPlayerUri(playerName)).build()
    }

    /**
     * Select a player with the specified username AND nick name
     */
    private fun createNickNameSelectionAndArgs(username: String, nickName: String) =
            "${PlayPlayers.USER_NAME}=? AND play_players.${PlayPlayers.NAME}!=?" to arrayOf(username, nickName)

    /**
     * Select a player with the specified name and no username
     */
    private fun createNonUserPlayerSelectionAndArgs(playerName: String) =
            "play_players.${PlayPlayers.NAME}=? AND (${PlayPlayers.USER_NAME}=? OR ${PlayPlayers.USER_NAME} IS NULL)" to arrayOf(playerName, "")
}
