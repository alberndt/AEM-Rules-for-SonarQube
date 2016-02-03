package com.cognifide.aemrules.checks;

import javax.annotation.Nonnull;

import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.TypeTree;

import com.cognifide.aemrules.checks.visitors.SessionUsageVisitor;
import com.cognifide.aemrules.tag.Tags;

/**
 * @author Krzysztof Watral
 */
@Rule(
	key = ModelsShouldNotUseSessionCheck.RULE_KEY,
	name = ModelsShouldNotUseSessionCheck.RULE_MESSAGE,
	priority = Priority.BLOCKER,
	tags = Tags.AEM
)
public class ModelsShouldNotUseSessionCheck extends BaseTreeVisitor implements JavaFileScanner {

	public static final String RULE_KEY = "AEM-9";

	public static final String RULE_MESSAGE
		= "Objects annotated by @SliceResource should not use (except: constructor, com.cognifide.slice.api.model.InitializableModel.afterCreated()) and return any session based object.";

	private static final String INITIALIZABLE_MODEL_INTERFACE = "com.cognifide.slice.api.model.InitializableModel";

	private JavaFileScannerContext context;

	private CheckAppliedAnnotationsVisitor annotations;

	private boolean implementedInitializableModel;

	@Override
	public void scanFile(@Nonnull JavaFileScannerContext context) {
		this.context = context;
		annotations = new CheckAppliedAnnotationsVisitor();
		context.getTree().accept(annotations);
		scan(context.getTree());
	}

	@Override
	public void visitClass(ClassTree classTree) {
		for (TypeTree typeTree : classTree.superInterfaces()) {
			implementedInitializableModel |= isOfType(typeTree, INITIALIZABLE_MODEL_INTERFACE);
		}
		super.visitClass(classTree);
	}

	@Override
	public void visitMethod(MethodTree tree) {
		if (annotations.hasSliceResourceAnnotation()) {
			if (isRegularMethod(tree)) {
				checkSessionUsage(tree);
			}
		}
		super.visitMethod(tree);
	}

	private boolean isRegularMethod(MethodTree tree) {
		boolean constructor = tree.is(MethodTree.Kind.CONSTRUCTOR);
		boolean afterCreated = implementedInitializableModel && "afterCreated".equals(tree.symbol().name());
		return !constructor && !afterCreated;
	}

	private void checkSessionUsage(MethodTree tree) {
		SessionUsageVisitor sessionUsageVisitor = new SessionUsageVisitor();
		tree.accept(sessionUsageVisitor);

		if (null != sessionUsageVisitor.getReturnStatementTree()) {
			context.reportIssue(this, sessionUsageVisitor.getReturnStatementTree(), RULE_MESSAGE);
		}

		for (MemberSelectExpressionTree sessionMember : sessionUsageVisitor.getSessionMemberSelect()) {
			context.reportIssue(this, sessionMember, RULE_MESSAGE);
		}
	}

	private static boolean isOfType(final TypeTree typeTree, final String fullyQualifiedName) {
		return typeTree.symbolType().is(fullyQualifiedName);
	}

	private class CheckAppliedAnnotationsVisitor extends BaseTreeVisitor {

		private static final String SLICE_RESOURCE_ANNOTATION = "com.cognifide.slice.mapper.annotation.SliceResource";

		private boolean sliceResourceAnnotation;

		@Override
		public void visitAnnotation(AnnotationTree annotationTree) {
			sliceResourceAnnotation |= isOfType(annotationTree.annotationType(), SLICE_RESOURCE_ANNOTATION);
			super.visitAnnotation(annotationTree);
		}

		public boolean hasSliceResourceAnnotation() {
			return sliceResourceAnnotation;
		}
	}
}
