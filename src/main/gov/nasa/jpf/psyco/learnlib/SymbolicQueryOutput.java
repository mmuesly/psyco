/*
 * Copyright (C) 2015, United States Government, as represented by the 
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The PSYCO: A Predicate-based Symbolic Compositional Reasoning environment 
 * platform is licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0. 
 *
 * Unless required by applicable law or agreed to in writing, software distributed 
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the 
 * specific language governing permissions and limitations under the License.
 */
package gov.nasa.jpf.psyco.learnlib;

import gov.nasa.jpf.jdart.constraints.Path;
import static gov.nasa.jpf.jdart.constraints.PathState.DONT_KNOW;
import static gov.nasa.jpf.jdart.constraints.PathState.ERROR;
import static gov.nasa.jpf.jdart.constraints.PathState.OK;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;

public class SymbolicQueryOutput {

  public static enum Output {
    OK, ERROR, DONT_KNOW
  };

  public static final SymbolicQueryOutput ERROR
          = new SymbolicQueryOutput(Output.ERROR);

  public static final SymbolicQueryOutput OK
          = new SymbolicQueryOutput(Output.OK);

  public static final SymbolicQueryOutput DONT_KNOW
          = new SymbolicQueryOutput(Output.DONT_KNOW);

  public static final SymbolicQueryOutput NONE
          = new SymbolicQueryOutput();

  private final EnumSet<Output> output;

  private SymbolicQueryOutput() {
    this.output = EnumSet.noneOf(Output.class);
  }

  private SymbolicQueryOutput(Output... out) {
    this.output = EnumSet.copyOf(Arrays.asList(out));
  }

  public SymbolicQueryOutput(SymbolicQueryOutput... out) {
    EnumSet<Output> temp = EnumSet.noneOf(Output.class);
    for (SymbolicQueryOutput o : out) {
      temp.addAll(o.output);
    }
    this.output = temp;
  }

  public SymbolicQueryOutput(SymbolicExecutionResult result) {

    EnumSet<SymbolicQueryOutput.Output> _output
            = EnumSet.noneOf(SymbolicQueryOutput.Output.class);

    if (!result.getOk().isEmpty()) {
      _output.add(SymbolicQueryOutput.Output.OK);
    }

    if (!result.getDontKnow().isEmpty()) {
      _output.add(SymbolicQueryOutput.Output.DONT_KNOW);
    }

    for (Path p : result.getError()) {
      if (!p.getErrorResult().getExceptionClass().equals(
              gov.nasa.jpf.psyco.oracles.JDartOracle.ALPHABET_CLASS 
                      + "$TotallyPsyco")) {

        _output.add(SymbolicQueryOutput.Output.ERROR);
        break;
      }
    }

    this.output = EnumSet.copyOf(_output);
  }

  public boolean isUniform() {
    return this.output.size() == 1;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 83 * hash + Objects.hashCode(this.output);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SymbolicQueryOutput other = (SymbolicQueryOutput) obj;
    if (!Objects.equals(this.output, other.output)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return Arrays.toString(this.output.toArray());
  }

  public static SymbolicQueryOutput forPath(Path p) {
    switch (p.getState()) {
      case OK:
        return SymbolicQueryOutput.OK;
      case ERROR:
        return SymbolicQueryOutput.ERROR;
      case DONT_KNOW:
        return SymbolicQueryOutput.DONT_KNOW;
      default:
        return SymbolicQueryOutput.NONE;
    }
  }
}
