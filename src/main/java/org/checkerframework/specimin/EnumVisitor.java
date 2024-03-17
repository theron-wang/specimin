package org.checkerframework.specimin;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This visitor updates the list of used classes based on the enum constants used inside the target
 * methods.
 */
public class EnumVisitor extends VoidVisitorAdapter<Void> {

  /** Set of classes used by the target method. */
  private Set<String> usedClass;

  /** Chech whether the visitor is inside the target method. */
  private boolean insideTargetMethod = false;

  /** The current qualified name of this class. */
  private String classFQName = "";

  /** The set of signatures of target methods. */
  private Set<String> targetMethods = new HashSet<>();

  /** Constructs an EnumConstructorVisitor with the provided set of used members. */
  public EnumVisitor(List<String> targetMethods) {
    this.usedClass = new HashSet<>();
    this.targetMethods.addAll(targetMethods);
  }

  /**
   * Get the set of used members.
   *
   * @return the set of used members.
   */
  public Set<String> getUsedClass() {
    return usedClass;
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration decl, Void p) {
    if (decl.isNestedType()) {
      this.classFQName += "." + decl.getName().toString();
    } else if (!decl.isLocalClassDeclaration()) {
      if (!this.classFQName.equals("")) {
        throw new UnsupportedOperationException(
            "Attempted to enter an unexpected kind of class: "
                + decl.getFullyQualifiedName()
                + " but already had a set classFQName: "
                + classFQName);
      }
      // Should always be present.
      this.classFQName = decl.getFullyQualifiedName().orElseThrow();
    }
    super.visit(decl, p);
    if (decl.isNestedType()) {
      this.classFQName = this.classFQName.substring(0, this.classFQName.lastIndexOf('.'));
    } else if (!decl.isLocalClassDeclaration()) {
      this.classFQName = "";
    }
  }

  @Override
  public void visit(MethodDeclaration methodDeclaration, Void arg) {
    String methodQualifiedSignature =
        this.classFQName
            + "#"
            + TargetMethodFinderVisitor.removeMethodReturnTypeAndAnnotations(
                methodDeclaration.getDeclarationAsString(false, false, false));
    if (targetMethods.contains(methodQualifiedSignature)) {
      boolean oldInsideTargetMethod = insideTargetMethod;
      insideTargetMethod = true;
      super.visit(methodDeclaration, arg);
      insideTargetMethod = oldInsideTargetMethod;
    } else {
      super.visit(methodDeclaration, arg);
    }
  }

  @Override
  public void visit(FieldAccessExpr fieldAccessExpr, Void arg) {
    if (insideTargetMethod) {
      updateUsedClassForPotentialEnum(fieldAccessExpr);
    }
    super.visit(fieldAccessExpr, arg);
  }

  @Override
  public void visit(NameExpr nameExpr, Void arg) {
    if (insideTargetMethod) {
      updateUsedClassForPotentialEnum(nameExpr);
    }
    super.visit(nameExpr, arg);
  }

  public void updateUsedClassForPotentialEnum(Expression expression) {
    ResolvedValueDeclaration resolvedField;
    // JavaParser sometimes consider an enum usage a field access expression, sometimes a name
    // expression.
    if (expression.isFieldAccessExpr()) {
      try {
        resolvedField = expression.asFieldAccessExpr().resolve();
      } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
        return;
      }
    } else if (expression.isNameExpr()) {
      try {
        resolvedField = expression.asNameExpr().resolve();
      } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
        return;
      }
    } else {
      throw new RuntimeException(
          "Unexpected parameter for updateUsedClassForPotentialEnum: " + expression);
    }

    if (resolvedField.isEnumConstant()) {
      ResolvedType correspondingEnumDeclaration = resolvedField.asEnumConstant().getType();
      usedClass.add(correspondingEnumDeclaration.describe());
    }
  }
}
