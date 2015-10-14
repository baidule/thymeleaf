/*
 * =============================================================================
 * 
 *   Copyright (c) 2011-2014, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.thymeleaf.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.engine.TemplateManager;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.inline.NoOpInliner;
import org.thymeleaf.templateresolver.TemplateResolution;
import org.thymeleaf.util.Validate;

/**
 * <p>
 *   Basic non-web implementation of the {@link IEngineContext} interface.
 * </p>
 * <p>
 *   This is the context implementation that will be used by default for non-web processing. Note this is an
 *   internal implementation, and that there is no reason for users' code to directly reference or use it instead
 *   of its implemented interfaces.
 * </p>
 * <p>
 *   This class is NOT thread-safe. Thread-safety is not a requirement for context implementations.
 * </p>
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.0
 *
 */
final class EngineContext extends AbstractEngineContext implements IEngineContext {

    /*
     * This class is in charge of managing the map of variables in place at each moment in the template processing,
     * by taking care of the different model levels the process is traversing and storing local variables only
     * for the levels they correspond to.
     */

    private static final int DEFAULT_LEVELS_SIZE = 3;
    private static final int DEFAULT_MAP_SIZE = 5;


    private int level = 0;
    private int index = 0;
    private int[] levels;
    private HashMap<String,Object>[] maps;
    private SelectionTarget[] selectionTargets;
    private IInliner[] inliners;
    private TemplateResolution[] templateResolutions;

    private SelectionTarget lastSelectionTarget = null;
    private IInliner lastInliner = null;
    private TemplateResolution lastTemplateResolution = null;

    private final List<TemplateResolution> templateResolutionStack;

    private static final Object NON_EXISTING = new Object() {
        @Override
        public String toString() {
            return "(*removed*)";
        }
    };



    /*
     * There is no reason for a user to directly create an instance of this - they should create Context or
     * WebContext instances instead.
     */
    EngineContext(
            final IEngineConfiguration configuration,
            final TemplateResolution templateResolution,
            final Locale locale,
            final Map<String, Object> variables) {

        super(configuration, locale);

        this.levels = new int[DEFAULT_LEVELS_SIZE];
        this.maps = (HashMap<String, Object>[]) new HashMap<?,?>[DEFAULT_LEVELS_SIZE];
        this.selectionTargets = new SelectionTarget[DEFAULT_LEVELS_SIZE];
        this.inliners = new IInliner[DEFAULT_LEVELS_SIZE];
        this.templateResolutions = new TemplateResolution[DEFAULT_LEVELS_SIZE];
        Arrays.fill(this.levels, Integer.MAX_VALUE);
        Arrays.fill(this.maps, null);
        Arrays.fill(this.selectionTargets, null);
        Arrays.fill(this.inliners, null);
        Arrays.fill(this.templateResolutions, null);
        this.levels[0] = 0;
        this.templateResolutions[0] = templateResolution;

        this.templateResolutionStack = new ArrayList<TemplateResolution>(DEFAULT_LEVELS_SIZE);
        this.templateResolutionStack.add(templateResolution);

        if (variables != null) {
            setVariables(variables);
        }

    }


    public boolean containsVariable(final String name) {
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.maps[n] != null && this.maps[n].containsKey(name)) {
                // The most modern entry we find for this key could be a removal --> false
                return (this.maps[n].get(name) != NON_EXISTING);
            }
        }
        return false;
    }


    public Object getVariable(final String key) {
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.maps[n] != null && this.maps[n].containsKey(key)) {
                final Object result = this.maps[n].get(key);
                if (result == NON_EXISTING) {
                    return null;
                }
                return result;
            }
        }
        return null;
    }


    public Set<String> getVariableNames() {

        final Set<String> variableNames = new HashSet<String>();
        int n = this.index + 1;
        int i = 0;
        while (n-- != 0) {
            if (this.maps[i] != null) {
                for (final Map.Entry<String,Object> mapEntry : this.maps[i].entrySet()) {
                    if (mapEntry.getValue() == NON_EXISTING) {
                        variableNames.remove(mapEntry.getKey());
                        continue;
                    }
                    variableNames.add(mapEntry.getKey());
                }
            }
            i++;
        }
        return variableNames;

    }


    public void setVariable(final String name, final Object value) {

        ensureLevelInitialized(DEFAULT_MAP_SIZE);

        if (value == NON_EXISTING && this.level == 0) {
            this.maps[this.index].remove(name);
        } else {
            this.maps[this.index].put(name, value);
        }

    }


    public void setVariables(final Map<String, Object> variables) {

        if (variables == null || variables.isEmpty()) {
            return;
        }

        ensureLevelInitialized(Math.max(DEFAULT_MAP_SIZE, variables.size() + 2));

        this.maps[this.index].putAll(variables);

    }




    public void removeVariable(final String name) {
        if (containsVariable(name)) {
            setVariable(name, NON_EXISTING);
        }
    }




    public boolean isVariableLocal(final String name) {
        int n = this.index + 1;
        while (n-- > 1) { // variables at n == 0 are not local!
            if (this.maps[n] != null && this.maps[n].containsKey(name)) {
                final Object result = this.maps[n].get(name);
                if (result == NON_EXISTING) {
                    return false; // We return false for "non existing"
                }
                return true;
            }
        }
        return false; // We return false for "non existing"
    }




    public boolean hasSelectionTarget() {
        if (this.lastSelectionTarget != null) {
            return true;
        }
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.selectionTargets[n] != null) {
                return true;
            }
        }
        return false;
    }


    public Object getSelectionTarget() {
        if (this.lastSelectionTarget != null) {
            return this.lastSelectionTarget.selectionTarget;
        }
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.selectionTargets[n] != null) {
                this.lastSelectionTarget = this.selectionTargets[n];
                return this.lastSelectionTarget.selectionTarget;
            }
        }
        return null;
    }


    public void setSelectionTarget(final Object selectionTarget) {
        ensureLevelInitialized(DEFAULT_MAP_SIZE);
        this.lastSelectionTarget = new SelectionTarget(selectionTarget);
        this.selectionTargets[this.index] = this.lastSelectionTarget;
    }




    public IInliner getInliner() {
        if (this.lastInliner != null) {
            if (this.lastInliner == NoOpInliner.INSTANCE) {
                return null;
            }
            return this.lastInliner;
        }
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.inliners[n] != null) {
                this.lastInliner = this.inliners[n];
                if (this.lastInliner == NoOpInliner.INSTANCE) {
                    return null;
                }
                return this.lastInliner;
            }
        }
        return null;
    }


    public void setInliner(final IInliner inliner) {
        ensureLevelInitialized(DEFAULT_MAP_SIZE);
        // We use NoOpInliner.INSTACE in order to signal when inlining has actually been disabled
        this.lastInliner = (inliner == null? NoOpInliner.INSTANCE : inliner);
        this.inliners[this.index] = this.lastInliner;
    }




    public TemplateResolution getTemplateResolution() {
        if (this.lastTemplateResolution != null) {
            return this.lastTemplateResolution;
        }
        int n = this.index + 1;
        while (n-- != 0) {
            if (this.templateResolutions[n] != null) {
                this.lastTemplateResolution = this.templateResolutions[n];
                return this.lastTemplateResolution;
            }
        }
        return null;
    }


    public void setTemplateResolution(final TemplateResolution templateResolution) {
        Validate.notNull(templateResolution, "Template Resolution cannot be null");
        ensureLevelInitialized(DEFAULT_MAP_SIZE);
        this.lastTemplateResolution = templateResolution;
        this.templateResolutions[this.index] = this.lastTemplateResolution;
        this.templateResolutionStack.clear();
    }




    public List<TemplateResolution> getTemplateResolutionStack() {
        if (!this.templateResolutionStack.isEmpty()) {
            // If would have been empty if we had just decreased a level or added a new resolution
            return Collections.unmodifiableList(this.templateResolutionStack);
        }
        int n = this.index + 1;
        int i = 0;
        while (n-- != 0) {
            if (this.templateResolutions[i] != null) {
                this.templateResolutionStack.add(this.templateResolutions[i]);
            }
            i++;
        }

        return Collections.unmodifiableList(this.templateResolutionStack);
    }




    private void ensureLevelInitialized(final int requiredSize) {

        // First, check if the current index already signals the current level (in which case, everything is OK)
        if (this.levels[this.index] != this.level) {

            // The current level still had no index assigned -- we must do it, and maybe even grow structures

            this.index++; // This new index will be the one for our level

            if (this.levels.length == this.index) {
                final int[] newLevels = new int[this.levels.length + DEFAULT_LEVELS_SIZE];
                final HashMap<String,Object>[] newMaps = (HashMap<String, Object>[]) new HashMap<?,?>[this.maps.length + DEFAULT_LEVELS_SIZE];
                final SelectionTarget[] newSelectionTargets = new SelectionTarget[this.selectionTargets.length + DEFAULT_LEVELS_SIZE];
                final IInliner[] newInliners = new IInliner[this.inliners.length + DEFAULT_LEVELS_SIZE];
                final TemplateResolution[] newTemplateResolutions = new TemplateResolution[this.templateResolutions.length + DEFAULT_LEVELS_SIZE];
                Arrays.fill(newLevels, Integer.MAX_VALUE);
                Arrays.fill(newMaps, null);
                Arrays.fill(newSelectionTargets, null);
                Arrays.fill(newInliners, null);
                Arrays.fill(newTemplateResolutions, null);
                System.arraycopy(this.levels, 0, newLevels, 0, this.levels.length);
                System.arraycopy(this.maps, 0, newMaps, 0, this.maps.length);
                System.arraycopy(this.selectionTargets, 0, newSelectionTargets, 0, this.selectionTargets.length);
                System.arraycopy(this.inliners, 0, newInliners, 0, this.inliners.length);
                System.arraycopy(this.templateResolutions, 0, newTemplateResolutions, 0, this.templateResolutions.length);
                this.levels = newLevels;
                this.maps = newMaps;
                this.selectionTargets = newSelectionTargets;
                this.inliners = newInliners;
                this.templateResolutions = newTemplateResolutions;
            }

            this.levels[this.index] = this.level;

        }

        if (this.maps[this.index] == null) {
            // The map for this level has not yet been created
            this.maps[this.index] = new HashMap<String,Object>(requiredSize, 1.0f);
        }

    }




    public void increaseLevel() {
        this.level++;
    }


    public void decreaseLevel() {

        Validate.isTrue(this.level > 0, "Cannot decrease variable map level below 0");

        if (this.levels[this.index] == this.level) {

            this.levels[this.index] = Integer.MAX_VALUE;
            if (this.maps[this.index] != null) {
                this.maps[this.index].clear();
            }
            this.selectionTargets[this.index] = null;
            this.inliners[this.index] = null;
            this.templateResolutions[this.index] = null;
            this.index--;

            // These might not belong to this level, but just in case...
            this.lastSelectionTarget = null;
            this.lastInliner = null;
            this.lastTemplateResolution = null;
            this.templateResolutionStack.clear();

        }

        this.level--;

    }


    public String getStringRepresentationByLevel() {

        final StringBuilder strBuilder = new StringBuilder();
        strBuilder.append('{');
        int n = this.index + 1;
        while (n-- != 0) {
            final Map<String,Object> levelVars = new LinkedHashMap<String, Object>();
            if (this.maps[n] != null) {
                final List<String> entryNames = new ArrayList<String>(this.maps[n].keySet());
                Collections.sort(entryNames);
                for (final String name : entryNames) {
                    final Object value = this.maps[n].get(name);
                    if (value == NON_EXISTING) {
                        // We only have to add this if it is really removing anything
                        int n2 = n;
                        while (n2-- != 0) {
                            if (this.maps[n2] != null && this.maps[n2].containsKey(name)) {
                                if (this.maps[n2].get(name) != NON_EXISTING) {
                                    levelVars.put(name, value);
                                }
                                break;
                            }
                        }
                        continue;
                    }
                    levelVars.put(name, value);
                }
            }
            if (n == 0 || !levelVars.isEmpty() || this.selectionTargets[n] != null || this.inliners[n] != null || this.templateResolutions[n] != null) {
                if (strBuilder.length() > 1) {
                    strBuilder.append(',');
                }
                strBuilder.append(this.levels[n] + ":");
                if (!levelVars.isEmpty() || n == 0) {
                    strBuilder.append(levelVars);
                }
                if (this.selectionTargets[n] != null) {
                    strBuilder.append("<" + this.selectionTargets[n].selectionTarget + ">");
                }
                if (this.inliners[n] != null) {
                    strBuilder.append("[" + this.inliners[n].getName() + "]");
                }
                if (this.templateResolutions[n] != null) {
                    strBuilder.append("(" + this.templateResolutions[n].getTemplate() + ")");
                }
            }
        }
        strBuilder.append("}[");
        strBuilder.append(this.level);
        strBuilder.append(']');
        return strBuilder.toString();

    }




    @Override
    public String toString() {

        final Map<String,Object> equivalentMap = new LinkedHashMap<String, Object>();
        int n = this.index + 1;
        int i = 0;
        while (n-- != 0) {
            if (this.maps[i] != null) {
                final List<String> entryNames = new ArrayList<String>(this.maps[i].keySet());
                Collections.sort(entryNames);
                for (final String name : entryNames) {
                    final Object value = this.maps[i].get(name);
                    if (value == NON_EXISTING) {
                        equivalentMap.remove(name);
                        continue;
                    }
                    equivalentMap.put(name, value);
                }
            }
            i++;
        }
        final String textInliningStr = (getInliner() != null? "[" + getInliner().getName() + "]" : "" );
        final String templateResolutionStr = "(" + getTemplateResolution().getTemplate() + ")";
        return equivalentMap.toString() + (hasSelectionTarget()? "<" + getSelectionTarget() + ">" : "") + textInliningStr + templateResolutionStr;

    }




    /*
     * This class works as a wrapper for the selection target, in order to differentiate whether we
     * have set a selection target, we have not, or we have set it but it's null
     */
    private static final class SelectionTarget {

        final Object selectionTarget;

        SelectionTarget(final Object selectionTarget) {
            super();
            this.selectionTarget = selectionTarget;
        }

    }


}