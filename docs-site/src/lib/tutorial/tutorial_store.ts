import type { Tutorial } from './tutorial_schema';

export type TutorialStore = {
  load(url: string): Promise<Tutorial>;
  currentStep: number;
  setCurrentStep(index: number): void;
};

export function createTutorialStore(): TutorialStore {
  return {
    currentStep: 0,
    async load(url: string) {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`Unable to load tutorial JSON: ${response.status} ${response.statusText}`);
      }
      const parsed = (await response.json()) as Tutorial;
      if (!parsed.steps?.length) {
        throw new Error(`Tutorial ${parsed.id ?? url} has no steps.`);
      }
      return parsed;
    },
    setCurrentStep(index: number) {
      this.currentStep = index;
    }
  };
}
