/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.jpf.psyco.search.transitionSystem;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Variable;
import gov.nasa.jpf.constraints.expressions.Constant;
import gov.nasa.jpf.constraints.expressions.NumericBooleanExpression;
import gov.nasa.jpf.constraints.expressions.NumericCompound;
import gov.nasa.jpf.constraints.expressions.NumericOperator;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.types.Type;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.jdart.constraints.Path;
import static gov.nasa.jpf.jdart.constraints.PathState.OK;
import static gov.nasa.jpf.jdart.constraints.PathState.ERROR;
import gov.nasa.jpf.psyco.search.SymbolicSearchEngine;
import gov.nasa.jpf.psyco.search.collections.StateImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author mmuesly
 */
public class Transition {
  Path path;
  VariableRestrictionsVisitor visitor;
  List<Variable> modified;
  List<Variable> guardVariables;
  List<Variable> stateVariables;
  Map<Variable, Boolean> lowerBound;
  Map<Variable, Boolean> upperBound;
  private static final Logger logger = 
          Logger.getLogger(SymbolicSearchEngine.getSearchLoggerName());


  public Transition(Path p){
    this.path = p;
    if(isOK()){
      visitor = new VariableRestrictionsVisitor();
      stateVariables = new ArrayList<Variable>(path.getPostCondition()
              .getConditions().keySet());
      calculateModified();
      calculateGuardVariables();
    }
  }

  public boolean isStutterTransition(){
    return modified.isEmpty();
  }

  public boolean isOK(){
    return path.getState() == OK;
  }

  public boolean isError(){
    return path.getState() == ERROR;
  }

  //assumes this instance has a OkPath
  private void calculateModified(){
    modified = new ArrayList<>();
    Map<Variable<?>, Expression<?>> transitionEffekt = path.getPostCondition().getConditions();
    for(Variable variable : transitionEffekt.keySet()){
      Expression value = transitionEffekt.get(variable);
      if(value instanceof Variable && value.equals(variable)){
        continue;
      }
      modified.add(variable);
    }
  }

  private void calculateGuardVariables(){
    initializeBoundsToFalse();
    Expression<Boolean> guard = path.getPathCondition();
    guardVariables = new ArrayList<Variable>(
            ExpressionUtil.freeVariables(guard));
    List<NumericBooleanExpression> guardLimitations = new ArrayList<>();
    guard.accept(visitor,guardLimitations);
    analyzeVariableLimitations(guardLimitations);
    logger.finest("\ngov.nasa.jpf.psyco.search.Transition.calculateGuardVariables()");
    logger.finest("path: " + path.toString());
    logger.finest("guard Variables: " + guardLimitations.toString());
    logger.finest("upperBounds: " + upperBound.toString());
    logger.finest("lowerBonds: " + lowerBound.toString());
    logger.finest("");
    return;
  }

  private void initializeBoundsToFalse(){
    lowerBound = initializeBoundToFalse(lowerBound);
    upperBound = initializeBoundToFalse(upperBound);
  }
  
  private Map<Variable, Boolean> initializeBoundToFalse(Map<Variable, Boolean> bound){
    bound = new HashMap<>();
    for(Variable var: stateVariables){
      bound.put(var, Boolean.FALSE);
    }
    return bound;
  }
  private void analyzeVariableLimitations(List<NumericBooleanExpression> guardLimits){
    for(NumericBooleanExpression expr: guardLimits){
      switch(expr.getComparator()){
        case GT:
        case GE:
          addLimit(expr.getRight(), expr.getLeft(), true, false);
          addLimit(expr.getLeft(), expr.getRight(), false, true);
          break;
        case LT:
        case LE:
          addLimit(expr.getLeft(), expr.getRight(), true, false);
          addLimit(expr.getRight(), expr.getLeft(), false, true);
          break;
        case EQ:
        case NE:
   //       if(expr.get)
        default:
          System.out.println("gov.nasa.jpf.psyco.search.transitionSystem.Transition.analyzeVariableLimitations()");
          System.out.println("Unused expr: " + expr.toString());
          break;
      }
    }
  }

  private void addLimit(Expression smaller, Expression limit, boolean upperLimit,boolean lowerLimit){
    Set<Variable<?>> vars = ExpressionUtil.freeVariables(smaller);
    if(limit instanceof Constant){
      for(Variable var: vars){
        markLimits(var, upperLimit,lowerLimit);
      }
    }
  }
  
  public void markLimits(Variable var, boolean upper, boolean lower){
    if(upper){
          markTrue(var, upperBound);
    }
    if(lower){
      markTrue(var, lowerBound);
    }
  }
  public void markTrue(Variable var, Map<Variable, Boolean> bound){
    bound.put(var, Boolean.TRUE);
  }
  
  public Path getPath(){
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }

  public boolean isLimitedTransition(){
    if(isOK()){
      Map<Variable<?>, Expression<?>> pathEffects = path.getPostCondition().getConditions();
//      System.out.println("\n\ngov.nasa.jpf.psyco.search.transitionSystem.Transition.isLimitedTransition()");
//      System.out.println(path.toString());
      for(Variable var: pathEffects.keySet()){
        Expression result = pathEffects.get(var);
//        System.out.println("var: " + var+ " result: " + result.toString() + " class: " + result.getClass());
        if(result instanceof NumericCompound){
          boolean limit = analyzeNumericCompound(var,(NumericCompound) result);
//          System.out.println("gov.nasa.jpf.psyco.search.transitionSystem.Transition.isLimitedTransition()");
//          System.out.println("NumericCompound: " + result.toString() + "limit: " + limit);
          return limit;
        }
      }
    }
    return true;
  }

  private boolean analyzeNumericCompound(Variable var, NumericCompound result) {
    Set<Variable<?>> variables  = ExpressionUtil.freeVariables(result);
    variables.remove(var);
    if(!variables.isEmpty()){
      return true;
    }
    int delta = evaluateNumericCompound(var, result);
    if(delta < 0 && lowerBound.get(var)){
      return true;
    }
    if(delta > 0 && upperBound.get(var)){
      return true;
    }
    return false;
  }
  private int evaluateNumericCompound(Variable var, NumericCompound result){
    Expression left = result.getLeft(),
               right = result.getRight();
    int leftValue, rightValue;
    try{
      leftValue = evaluateChild(var, left);
      rightValue = evaluateChild(var, right);
      return calculate(leftValue, rightValue, result.getOperator());
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(42);
    }
    return 0;
  }
  
  private int evaluateChild(Variable var, Expression side) throws Exception{
    if(side instanceof Constant){
      Type type = side.getType();
      if((type == BuiltinTypes.INTEGER)
          || type == BuiltinTypes.SINT16
          || type == BuiltinTypes.SINT32
          || type == BuiltinTypes.SINT64
          || type == BuiltinTypes.SINT8
          || type == BuiltinTypes.UINT16){
        return getIntegerValue((Constant) side);
      }
      throw new Exception("No Integer type here.");
    }else if(side instanceof NumericCompound){
      return evaluateNumericCompound(var, (NumericCompound) side);
    }else if(side instanceof Variable){
      Variable sideVar = (Variable) side;
      if(sideVar.equals(var)){
        return 0;
      }
      throw new Exception("Here should not be a variable.");
    }
    else{
      System.out.println("gov.nasa.jpf.psyco.search.transitionSystem.Transition.evaluateChild()");
      System.out.println(side.getClass());
      System.out.println(side.toString() + side.getType().toString());
      throw new Exception("Cannot convert subpart");
    }
  }
  private int getIntegerValue(Constant left){
    return new Integer(left.getValue().toString());
  }
  
  private int calculate(int leftValue, int rightValue, NumericOperator operator) throws Exception{
    switch(operator){
      case PLUS: return leftValue + rightValue;
      case MINUS: return leftValue - rightValue;
      case MUL: return leftValue * rightValue;
      case REM:
      case DIV: throw new Exception("Div and REM not supported");
      default: return 0;
    }
  }

  public Expression getGuardCondition() {
    return path.getPathCondition();
  }

  public StateImage applyOn(StateImage alreadyReachedStates, TransitionHelper helper) {
    if(isOK()){
      logger.fine(path.toString());
      return helper.applyTransition(alreadyReachedStates, this);
    }else if(isError()){
      return helper.applyError(alreadyReachedStates, this);
    }else{
      System.out.println("gov.nasa.jpf.psyco.search.transitionSystem.Transition.applyOn()");
      System.out.println("Cannot deal with dont Know paths");
      System.exit(42);
    }
    return null;
  }

  public String getError() {
    if(isError()){
      return path.getErrorResult().getExceptionClass();
    }
    return "";
  }

  public boolean isGuardSymbolicConstant() {
    Expression guard = getGuardCondition();
    return ExpressionUtil.isTrue(guard);
  }

  public Expression getTransitionEffect(Variable oldVariable) {
    Map<Variable<?>, Expression<?>> pathResults = path.getPostCondition().getConditions();
    return pathResults.getOrDefault(oldVariable, null);
  }
}
