import type { TutorialAction, TutorialSuccessCondition } from './tutorial_schema';

export type CommandResult = {
  success: boolean;
  message?: string;
  payload?: unknown;
};

export type ConsoleResult = CommandResult & {
  outputLines?: string[];
};

export interface OctodrawTutorialHost {
  loadModel(source: string | object): Promise<void>;
  resetScene(): Promise<void>;
  runCommand(commandId: string): Promise<CommandResult>;
  runScript(script: string): Promise<ConsoleResult>;
  setCamera(camera: string | Record<string, unknown>): Promise<void>;
  captureScreenshot(): Promise<Blob>;
  highlight(selector: unknown): Promise<void>;
  clearHighlights(): Promise<void>;
  getState(): Promise<unknown>;
}

export type TutorialRuntime = {
  executeAction(action: TutorialAction): Promise<CommandResult>;
  checkSuccess(condition: TutorialSuccessCondition): Promise<CommandResult>;
};

export function createTutorialRuntime(host?: Partial<OctodrawTutorialHost>): TutorialRuntime {
  return {
    async executeAction(action) {
      if (action.type === 'none') {
        return { success: true, message: 'No action required.' };
      }
      if (action.type === 'command' && host?.runCommand) {
        return host.runCommand(action.commandId);
      }
      if (action.type === 'script' && host?.runScript) {
        return host.runScript(action.script);
      }
      if (action.type === 'camera' && host?.setCamera) {
        await host.setCamera(action.state ?? action.preset ?? 'overview');
        return { success: true };
      }
      if (action.type === 'loadScene' && host?.loadModel) {
        await host.loadModel(action.scene);
        return { success: true };
      }
      if (action.type === 'highlight' && host?.highlight) {
        await host.highlight(action.target);
        return { success: true };
      }
      return {
        success: true,
        message: `${action.type} action recorded; runtime integration is pending.`
      };
    },

    async checkSuccess(condition) {
      if (condition.type === 'manual') {
        return { success: true, message: condition.prompt };
      }
      return {
        success: true,
        message: `${condition.type} success check is defined but not yet connected to webcomponent state.`
      };
    }
  };
}
