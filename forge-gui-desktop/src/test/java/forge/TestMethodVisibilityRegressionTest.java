// REFORGE COMMANDER EXTENSION
package forge;

import forge.card.CardDbCardMockReforgeTestCase;
import forge.card.CardRequestReforgeTest;
import forge.deck.DeckRecognizerReforgeTest;
import forge.item.DeckHintsReforgeTest;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Validates that Reforge extension test classes exist and have public test methods.
 * Upstream test classes have package-private methods (TestNG 7.x compatibility issue).
 * Instead of modifying upstream, we create extension classes with public methods that
 * delegate to the upstream test logic.
 */
public class TestMethodVisibilityRegressionTest {

    @Test
    public void testCardRankerReforgeTestExists() {
        assertReforgeTestClassExists(CardRankerReforgeTest.class);
    }

    @Test
    public void testCardRankerReforgeTestHasPublicTestRankMethod() throws NoSuchMethodException {
        assertMethodIsPublic(CardRankerReforgeTest.class, "testRankPublic");
    }

    @Test
    public void testFCollectionReforgeTestExists() {
        assertReforgeTestClassExists(FCollectionReforgeTest.class);
    }

    @Test
    public void testFCollectionReforgeTestHasPublicTestCompletableFutureMethod() throws NoSuchMethodException {
        assertMethodIsPublic(FCollectionReforgeTest.class, "testCompletableFuturePublic");
    }

    @Test
    public void testRunReforgeTestExists() {
        assertReforgeTestClassExists(RunReforgeTest.class);
    }

    @Test
    public void testRunReforgeTestHasPublicTestMethod() throws NoSuchMethodException {
        assertMethodIsPublic(RunReforgeTest.class, "testPublic");
    }

    @Test
    public void testCardRequestReforgeTestExists() {
        assertReforgeTestClassExists(CardRequestReforgeTest.class);
    }

    @Test
    public void testCardRequestReforgeTestHasPublicFoilCardNameMethod() throws NoSuchMethodException {
        assertMethodIsPublic(CardRequestReforgeTest.class, "testComposeCardRequestWithCardNameAndFoilPublic");
    }

    @Test
    public void testCardDbCardMockReforgeTestCaseExists() {
        assertReforgeTestClassExists(CardDbCardMockReforgeTestCase.class);
    }

    @Test
    public void testCardDbCardMockReforgeTestCaseMethodsArePublic() throws NoSuchMethodException {
        String[] methodNames = {
                "testGetAllCardsOfaGivenNameAndPrintedInSetsPublic",
                "testGetAllCardsOfaGivenNameAndLegalInSetsPublic",
                "testCardRequestWithSetCodeAllInLowercasePublic",
                "testThatWithCardPreferenceSetAndNoRequestForSpecificEditionAlwaysReturnsPreferredArtPublic",
                "testGetDualAndDoubleCardsPublic"
        };
        for (String methodName : methodNames) {
            assertMethodIsPublic(CardDbCardMockReforgeTestCase.class, methodName);
        }
    }

    @Test
    public void testDeckHintsReforgeTestExists() {
        assertReforgeTestClassExists(DeckHintsReforgeTest.class);
    }

    @Test
    public void testDeckHintsReforgeTestAllTestMethodsArePublic() {
        assertAllTestAnnotatedMethodsArePublic(DeckHintsReforgeTest.class);
    }

    @Test
    public void testDeckRecognizerReforgeTestExists() {
        assertReforgeTestClassExists(DeckRecognizerReforgeTest.class);
    }

    @Test
    public void testDeckRecognizerReforgeTestAllTestMethodsArePublic() {
        assertAllTestAnnotatedMethodsArePublic(DeckRecognizerReforgeTest.class);
    }

    @Test
    public void testDetectionLogicFlagsNonPublicTestMethod() {
        List<String> nonPublicTestMethods = findNonPublicTestMethods(ClassWithNonPublicTestMethod.class);
        assertFalse(nonPublicTestMethods.isEmpty(), "Detection logic failed to flag a package-private @Test method");
        assertTrue(nonPublicTestMethods.contains("packagePrivateTestMethod"));
    }

    private void assertReforgeTestClassExists(Class<?> reforgeTestClass) {
        assertTrue(reforgeTestClass != null, reforgeTestClass.getSimpleName() + " must exist");
        assertTrue(reforgeTestClass.getSimpleName().contains("Reforge"),
                reforgeTestClass.getSimpleName() + " must follow Reforge naming convention");
    }

    private void assertMethodIsPublic(Class<?> testClass, String methodName) throws NoSuchMethodException {
        Method method = testClass.getDeclaredMethod(methodName);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                testClass.getName() + "#" + methodName
                        + "() must be public, otherwise TestNG silently skips it instead of running it");
    }

    private void assertAllTestAnnotatedMethodsArePublic(Class<?> testClass) {
        List<String> nonPublicTestMethods = findNonPublicTestMethods(testClass);
        assertTrue(nonPublicTestMethods.isEmpty(),
                "Found @Test method(s) in " + testClass.getName()
                        + " that are not public (TestNG silently skips these instead of running them): "
                        + nonPublicTestMethods);
    }

    private List<String> findNonPublicTestMethods(Class<?> testClass) {
        List<String> violations = new ArrayList<>();
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class) && !Modifier.isPublic(method.getModifiers())) {
                violations.add(method.getName());
            }
        }
        return violations;
    }

    private static class ClassWithNonPublicTestMethod {
        @Test
        void packagePrivateTestMethod() {
        }
    }
}
