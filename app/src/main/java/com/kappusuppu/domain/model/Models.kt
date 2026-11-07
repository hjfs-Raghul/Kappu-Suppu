package com.kappusuppu.domain.model

const val CHARACTERS_PER_PLAYER = 5

/** The two fixed identities of the game. */
enum class PlayerId(val displayName: String) {
    KAPPU("Kappu"),
    SUPPU("Suppu");

    val opponent: PlayerId get() = if (this == KAPPU) SUPPU else KAPPU
}

/** Drawing progression of a character. Gun / bullet are derived from the stage. */
enum class Stage(val number: Int) {
    CIRCLE(1),
    FACE(2),
    BODY(3),
    GUN(4),
    LOADED(5);

    val hasGun: Boolean get() = this == GUN || this == LOADED
    val bulletLoaded: Boolean get() = this == LOADED

    fun next(): Stage? = entries.getOrNull(ordinal + 1)
}

enum class Expression { NORMAL, CUNNING, SHOCKED }

/**
 * State of one character. Gun presence and bullet status are derived from [stage], so
 * combinations such as "stage 2 with a gun" or "stage 4 with a bullet" cannot be expressed.
 * A dead character can never hold a gun (it only dies while at stage 1-3).
 */
data class GameCharacter(
    val index: Int,
    val stage: Stage = Stage.CIRCLE,
    val alive: Boolean = true,
    val expression: Expression = Expression.NORMAL,
) {
    init {
        require(index in 0 until CHARACTERS_PER_PLAYER) { "Invalid character index $index" }
        require(alive || !stage.hasGun) { "A dead character cannot have a gun" }
        require(alive || expression == Expression.NORMAL) { "A dead character has no expression" }
    }

    val hasGun: Boolean get() = alive && stage.hasGun
    val bulletLoaded: Boolean get() = alive && stage.bulletLoaded

    /** Moves one stage forward (stage 1-4). */
    fun advance(): GameCharacter {
        check(alive) { "Dead characters cannot progress" }
        val next = checkNotNull(stage.next()) { "Loaded character cannot advance; it shoots" }
        return copy(stage = next)
    }

    /** Consumes the bullet: LOADED -> GUN. */
    fun consumeBullet(): GameCharacter {
        check(bulletLoaded) { "No bullet to consume" }
        return copy(stage = Stage.GUN, expression = Expression.NORMAL)
    }

    /** Gun (loaded or not) is destroyed: the character survives and returns to stage 3. */
    fun destroyGun(): GameCharacter {
        check(hasGun) { "No gun to destroy" }
        return copy(stage = Stage.BODY, expression = Expression.SHOCKED)
    }

    /** Character dies. Only possible without a gun. */
    fun kill(): GameCharacter {
        check(alive && !hasGun) { "Only a living, gun-less character can be killed" }
        return copy(alive = false, expression = Expression.NORMAL)
    }

    fun withExpression(e: Expression): GameCharacter = if (alive) copy(expression = e) else this
}

data class PlayerState(
    val id: PlayerId,
    val characters: List<GameCharacter> = List(CHARACTERS_PER_PLAYER) { GameCharacter(it) },
) {
    init {
        require(characters.size == CHARACTERS_PER_PLAYER) { "A player owns exactly five characters" }
        require(characters.withIndex().all { (i, c) -> c.index == i }) { "Character indices must be 0..4 in order" }
    }

    val isEliminated: Boolean get() = characters.none { it.alive }
    val aliveCount: Int get() = characters.count { it.alive }

    fun update(index: Int, transform: (GameCharacter) -> GameCharacter): PlayerState =
        copy(characters = characters.mapIndexed { i, c -> if (i == index) transform(c) else c })
}

data class PendingShot(val shooter: PlayerId, val shooterIndex: Int)

enum class Phase { AWAITING_PAGE, AWAITING_TARGET, GAME_OVER }

data class GameState(
    val kappu: PlayerState = PlayerState(PlayerId.KAPPU),
    val suppu: PlayerState = PlayerState(PlayerId.SUPPU),
    val currentPlayer: PlayerId = PlayerId.KAPPU,
    val turnNumber: Int = 1,
    val pendingShot: PendingShot? = null,
    val winner: PlayerId? = null,
) {
    init {
        require(kappu.id == PlayerId.KAPPU && suppu.id == PlayerId.SUPPU)
        require(pendingShot == null || winner == null) { "No pending shot after the game is over" }
    }

    /** Derived so that it can never disagree with the data. */
    val phase: Phase
        get() = when {
            winner != null -> Phase.GAME_OVER
            pendingShot != null -> Phase.AWAITING_TARGET
            else -> Phase.AWAITING_PAGE
        }

    fun player(id: PlayerId): PlayerState = if (id == PlayerId.KAPPU) kappu else suppu
    fun character(id: PlayerId, index: Int): GameCharacter = player(id).characters[index]

    fun withPlayer(state: PlayerState): GameState =
        if (state.id == PlayerId.KAPPU) copy(kappu = state) else copy(suppu = state)

    fun updateCharacter(id: PlayerId, index: Int, transform: (GameCharacter) -> GameCharacter): GameState =
        withPlayer(player(id).update(index, transform))

    companion object {
        fun newGame(): GameState = GameState()
    }
}

/** Which human (Player 1 / Player 2) plays which identity. Exactly one human per identity. */
data class PlayerAssignment(val player1: PlayerId) {
    val player2: PlayerId get() = player1.opponent

    fun humanLabel(id: PlayerId): String = if (id == player1) "Player 1" else "Player 2"

    val identities: Set<PlayerId> get() = setOf(player1, player2)
}
