package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import com.r3.corda.sdk.token.workflow.utilities.addToDistributionList
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Creates a [TransactionBuilder] with the preferred notary, the requested set of tokens as outputs and adds
 * [IssueTokenCommand]s for each group of states (grouped by [IssuedTokenType]. This flow can be called as an
 * [InitiatingFlow] or an inlined sub-flow.
 *
 * - No need to pass in any sessions when issuing to self but can pass in observer sessions if needed.
 * - There is an assumption that this flow can only be used by one issuer at a time.
 * - Tokens are issued to well known identities or confidential identities. This flow TODO this flow what
 * - Many tokens can be issued to a single party.
 * - Many tokens can be issued to many parties but usually only one.
 * - Observers can also be specified.
 * - can issue fungible and non fungible tokens at the same time.
 * - tokens can be issued to self or to another party
 * - The notary is selected from a config file or picked at random if no notary preference is available.
 * - This is not an initiating flow. There will also be an initiating version which is startable from the shell.
 * - This flow handles observers. Observers (via additional flow sessions) store the tx with ALL_VISIBLE.
 * - Can issue different types of token at the same time.
 */
@InitiatingFlow
class IssueTokensFlow<T : TokenType> private constructor(
        val tokens: List<AbstractToken<T>>,
        val existingSessions: Set<FlowSession>,
        val observers: Set<Party>
) : FlowLogic<SignedTransaction>() {
//    // NonFungible
//    constructor(token: NonFungibleToken<T>, session: FlowSession) : this() {
//        subFlow = IssueTokensFlow(listOf(token), listOf(session))
//    }
//
//    constructor(token: NonFungibleToken<T>, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(token), sessions)
//    }
//
//    constructor(tokenType: T, issuer: Party, holder: AbstractParty, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(tokenType issuedBy issuer heldBy holder), sessions)
//    }
//
//    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(issuedTokenType heldBy holder), sessions)
//    }
//
//    constructor(issuedTokenType: IssuedTokenType<T>, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(issuedTokenType heldBy issuedTokenType.issuer), sessions)
//    }
//
//    constructor(tokenType: T, issuer: Party, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(tokenType issuedBy issuer heldBy issuer), sessions)
//    }
//
//    // Fungible
//    constructor(tokens: FungibleToken<T>, session: FlowSession) : this() {
//        subFlow = IssueTokensFlow(listOf(tokens), listOf(session))
//    }
//
//    constructor(tokens: FungibleToken<T>, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(tokens), sessions)
//    }
//
//    constructor(tokenType: T, amount: Long, issuer: Party, holder: AbstractParty, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(amount of tokenType issuedBy issuer heldBy holder), sessions)
//    }
//
//    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(amount of issuedTokenType heldBy holder), sessions)
//    }
//
//    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer), sessions)
//    }
//
//    constructor(tokenType: T, amount: Long, issuer: Party, sessions: List<FlowSession>) : this() {
//        subFlow = IssueTokensFlow(listOf(amount of tokenType issuedBy issuer heldBy issuer), sessions)
//    }


    /** Standard constructors. */

    constructor(tokens: List<AbstractToken<T>>, observers: Set<Party>) : this(tokens, emptySet(), observers)

    constructor(tokens: List<AbstractToken<T>>, sessions: List<FlowSession>)
            : this(tokens, sessions.toSet(), emptySet())

    constructor(tokens: AbstractToken<T>, session: FlowSession) : this(listOf(tokens), setOf(session), emptySet())
    constructor(tokens: AbstractToken<T>, sessions: List<FlowSession>)
            : this(listOf(tokens), sessions.toSet(), emptySet())

    constructor(tokens: AbstractToken<T>, observers: Set<Party>) : this(listOf(tokens), emptySet(), observers)

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with a preferred notary or choose a random notary.
        val transactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        // Add all the specified tokens to the transaction. The correct commands and signing keys are also added.
        addIssueTokens(tokens, transactionBuilder)
        // Update the distribution list. This adds all proposed token holders to the distribution list for the token
        // type they are receiving. Observers are not currently added to the distribution list.
        addToDistributionList(tokens)

        // Create new sessions if this is started as a top level flow.
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(tokens, observers) else existingSessions
        // Determine which parties are participants and observers.
        return subFlow(ObserverAwareFinalityFlow(transactionBuilder, sessions))
    }
}