package com.anpurnama.f1_app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Multi-backstack navigation state — one persistent [NavBackStack] per
 * top-level tab, plus the currently-active tab. The "exit through home"
 * pattern is used: [startRoute] entries are always rendered, and pressing
 * back on a non-start tab's root switches to the start tab.
 *
 * Each tab's backstack is independently persistable (via [rememberNavBackStack])
 * and its entries are decorated with a [rememberSaveableStateHolderNavEntryDecorator]
 * (for composable `rememberSaveable` state) and a
 * [rememberViewModelStoreNavEntryDecorator] (for scoped ViewModel instances).
 * This means switching tabs preserves both UI state and ViewModel instances —
 * no data re-fetch on tab switch.
 */
class NavigationState(
    val startRoute: Route,
    /** Persisted across process death via [rememberSerializable] */
    topLevelRoute: MutableState<Route>,
    /** One backstack per tab, each persisted via [rememberNavBackStack] */
    val backStacks: Map<Route, NavBackStack<NavKey>>,
) {

    var currentRoute: Route by topLevelRoute

    /**
     * Converts the active backstacks into a flat list of decorated
     * [NavEntry] objects for [NavDisplay].
     *
     * Each backstack's entries get their own
     * [rememberSaveableStateHolderNavEntryDecorator] and
     * [rememberViewModelStoreNavEntryDecorator] so state and ViewModels
     * survive tab switches.
     *
     * Only stacks "in use" are returned: the start route is always
     * included (exit-through-home), plus the current active tab.
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (Route) -> NavEntry<Route>,
    ): List<NavEntry<Route>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val saveable = rememberSaveableStateHolderNavEntryDecorator<Route>()
            val viewModel = rememberViewModelStoreNavEntryDecorator<Route>()
            // saveable FIRST, then viewModel — order is load-bearing for
            // SavedStateHandle / UI state restoration on process death.
            val decorators = remember(saveable, viewModel) { listOf(saveable, viewModel) }
            @Suppress("UNCHECKED_CAST")
            val routeStack = stack as List<Route>
            rememberDecoratedNavEntries(
                backStack = routeStack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }
        return stacksInUse().flatMap { decoratedEntries[it].orEmpty() }
    }

    /**
     * Returns the list of stack keys that should currently be visible.
     * The start route is always first ("exit through home"); the current
     * tab is last (and therefore visible).
     *
     * This means pressing system back on a non-start tab's root switches
     * to the start tab, and pressing back on the start tab's root exits
     * the app.
     */
    private fun stacksInUse(): List<Route> =
        if (currentRoute == startRoute) listOf(startRoute)
        else listOf(startRoute, currentRoute)

    /** Switches to a top-level tab. */
    fun selectTab(route: Route) {
        require(route in backStacks) { "$route is not a top-level route" }
        currentRoute = route
    }

    /** Pushes [route] onto the current tab's backstack. */
    fun push(route: NavKey) {
        backStacks[currentRoute]?.add(route)
    }

    /**
     * Pops one entry from the current tab's backstack, or if at the root
     * of a non-start tab, switches to the start tab. Returns `false` when
     * the start tab is at its root and the app should exit.
     */
    fun pop(): Boolean {
        val stack = backStacks[currentRoute] ?: return false
        if (stack.size > 1) {
            stack.removeLastOrNull()
            return true
        }
        if (currentRoute != startRoute) {
            currentRoute = startRoute
            return true
        }
        return false
    }
}

/**
 * Creates and remembers the multi-backstack [NavigationState].
 *
 * @param startRoute the root tab (always rendered); must be in [topLevelRoutes].
 * @param topLevelRoutes the set of top-level tab routes.
 */
@Composable
fun rememberNavigationState(
    startRoute: Route,
    topLevelRoutes: Set<Route>,
): NavigationState {
    require(startRoute in topLevelRoutes) { "startRoute must be in topLevelRoutes" }

    val currentRouteState = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) { mutableStateOf<Route>(startRoute) }

    val backStacks = topLevelRoutes.associateWith { key ->
        rememberNavBackStack(key)
    }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = currentRouteState,
            backStacks = backStacks,
        )
    }
}

/**
 * Navigation actions — thin wrapper over [NavigationState].
 */
class Navigator(val state: NavigationState) {
    /**
     * Navigates to [route]. If it's a top-level tab, switches tabs
     * (preserving each tab's stack). Otherwise pushes the route onto
     * the current tab's backstack.
     */
    fun navigate(route: NavKey) {
        if (route is Route && route in state.backStacks) {
            state.selectTab(route)
        } else {
            state.push(route)
        }
    }

    /** Handles system back. Returns whether the back was consumed. */
    fun goBack(): Boolean = state.pop()
}
