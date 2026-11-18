// Copyright 2026 Tech Details. All rights reserved.
// Use of this file is governed by the LICENSE file at the root of this repository.

package redixapi;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;
import redix.support.Routes;
import redix.support.Routes.Route;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class KeepTheHistoryTest {
    private static final @NotNull String UNREAD = "History has no route that reads a sign-in, a refusal or a password change over HTTP; Go's internal/auth tests prove it";

    @Test(description = "A sign in is in the history with the user and the time", testName = "1f616e4a-4ed2-4752-9ba2-b4ab0d46260c", priority = 1, enabled = false)
    public void aSignInIsInTheHistoryWithTheUserAndTheTime() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "A wrong password is in the history against the user and never says what was typed", testName = "3d95fcc6-cac8-4902-8847-fb68051f5979", priority = 2, enabled = false)
    public void aWrongPasswordIsInTheHistoryAgainstTheUserAndNeverSaysWhatWasTyped() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "Trying while locked is a failed sign in", testName = "135f95fe-bde5-457e-b0b8-593e969b5e30", priority = 3, enabled = false)
    public void tryingWhileLockedIsAFailedSignIn() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "Someone inactive in every project is told a wrong password as anyone is", testName = "6c311879-8fe6-4428-b708-f0a33a6ab7d3", priority = 4, enabled = false)
    public void someoneInactiveInEveryProjectIsToldAWrongPasswordAsAnyoneIs() {
        throw new AssertionError(UNREAD + "; what they are told is SignInWithUsernameAndPasswordTest's sixth test");
    }

    @Test(description = "The wrong password that locks the account is recorded as a lockout", testName = "b62b5f4e-fa10-4d0f-b5a4-fbfa681f5920", priority = 5, enabled = false)
    public void theWrongPasswordThatLocksTheAccountIsRecordedAsALockout() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "A wrong current password is refused and counts toward the lockout", testName = "cc4b8750-ea53-4132-99ae-823b4fecb724", priority = 6, enabled = false)
    public void aWrongCurrentPasswordIsRefusedAndCountsTowardTheLockout() {
        throw new AssertionError(UNREAD + "; the refusal and the count are ChangePasswordTest's second test");
    }

    @Test(description = "An unknown username records nothing", testName = "2ff9b57f-af2c-4767-96fc-b982696c58f8", priority = 7, enabled = false)
    public void anUnknownUsernameRecordsNothing() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "A password change is in the history with the values before and after but never the password hash", testName = "eaa90980-c54a-480e-8c21-16cc1cd4c79b", priority = 8, enabled = false)
    public void aPasswordChangeIsInTheHistoryWithTheValuesBeforeAndAfterButNeverThePasswordHash() {
        throw new AssertionError(UNREAD);
    }

    @Test(description = "No record of the trail can be changed or deleted by anyone", testName = "ea3196ca-a944-41dd-87f7-47e0fc3cd9c2", priority = 9)
    public void noRecordOfTheTrailCanBeChangedOrDeletedByAnyone() {
        final @NotNull List<Route> routes = Routes.every();
        final @NotNull List<Route> history = routes.stream().filter(route -> route.path().contains("history")).toList();
        assertFalse(history.isEmpty(), "the document names no history route among " + routes.size());
        assertEquals(history.stream().filter(route -> !route.method().equals("GET")).toList(), List.of(), "a route changes the history");
    }
}
