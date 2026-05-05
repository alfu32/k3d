export type TutorialLevel = 'beginner' | 'intermediate' | 'advanced';

export type TutorialTarget = {
  kind: 'entity' | 'tool' | 'panel' | 'command' | 'scene' | 'screen-region';
  selector: string;
  label?: string;
};

export type TutorialAction =
  | { type: 'none'; description?: string }
  | { type: 'command'; commandId: string; description?: string; pendingDiscovery?: boolean }
  | { type: 'script'; script: string; description?: string; pendingValidation?: boolean }
  | { type: 'camera'; preset?: string; state?: Record<string, unknown>; description?: string }
  | { type: 'loadScene'; scene: string; description?: string }
  | { type: 'highlight'; target: TutorialTarget; description?: string };

export type TutorialSuccessCondition =
  | { type: 'manual'; prompt: string }
  | { type: 'command-active'; commandId: string }
  | { type: 'scene-state'; expression: string; description?: string }
  | { type: 'selection'; target: TutorialTarget; count?: number; description?: string };

export type TutorialStep = {
  id: string;
  title: string;
  body: string;
  notes?: string;
  actions?: TutorialAction[];
  success?: TutorialSuccessCondition[];
};

export type Tutorial = {
  id: string;
  title: string;
  level: TutorialLevel;
  estimatedMinutes: number;
  initialScene: string;
  steps: TutorialStep[];
};

export const tutorialActionTypes = ['none', 'command', 'script', 'camera', 'loadScene', 'highlight'] as const;
export const tutorialSuccessConditionTypes = ['manual', 'command-active', 'scene-state', 'selection'] as const;
