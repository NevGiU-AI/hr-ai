import { ComponentFixture, TestBed } from '@angular/core/testing';

import { JobOfferComponent } from './job-offer.component';
import { JobService } from '../../services/job.service';
import { of, throwError } from 'rxjs';
import { MOCK_JOB_GENERATION_RESPONSE, MOCK_JOB_GENERATION_REQUEST } from '../../mocks/job-data';
import { HttpErrorResponse } from '@angular/common/http';

describe('JobOfferComponent', () => {
  let component: JobOfferComponent;
  let fixture: ComponentFixture<JobOfferComponent>;
  let jobServiceSpy: jasmine.SpyObj<JobService>;

  beforeEach(async () => {
    jobServiceSpy = jasmine.createSpyObj('JobService', ['generateJobOffer']);
    await TestBed.configureTestingModule({
      imports: [JobOfferComponent],
      providers: [
        { provide: JobService, useValue: jobServiceSpy }
      ]
    })
      .compileComponents();

    fixture = TestBed.createComponent(JobOfferComponent);
    component = fixture.componentInstance;

  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('calls service and sets generatedJobOffer on success', () => {
    jobServiceSpy.generateJobOffer.and.returnValue(of(MOCK_JOB_GENERATION_RESPONSE));

    component.onGenerateOffer(MOCK_JOB_GENERATION_REQUEST);

    expect(jobServiceSpy.generateJobOffer).toHaveBeenCalledWith(MOCK_JOB_GENERATION_REQUEST);
    expect(component.originalBrief).toEqual(MOCK_JOB_GENERATION_REQUEST);
    expect(component.generatedJobOffer).toEqual(MOCK_JOB_GENERATION_RESPONSE);
    expect(component.isLoading).toBeFalse();
    expect(component.error).toBeNull();
  });


  it('onRegenerateOffer calls onGenerateOffer when originalBrief exists', () => {
    component.originalBrief = MOCK_JOB_GENERATION_REQUEST;
    const spy = spyOn(component, 'onGenerateOffer');

    component.onRegenerateOffer();

    expect(spy).toHaveBeenCalledWith(MOCK_JOB_GENERATION_REQUEST);
  });

  it('onRegenerateOffer does nothing when originalBrief is null', () => {
    component.originalBrief = null;
    const spy = spyOn(component, 'onGenerateOffer');

    component.onRegenerateOffer();

    expect(spy).not.toHaveBeenCalled();
  });

  it('displays the structured job-generation provider error', () => {
    jobServiceSpy.generateJobOffer.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 502,
      error: { message: 'AI job generation service is temporarily unavailable.' },
    })));

    component.onGenerateOffer(MOCK_JOB_GENERATION_REQUEST);

    expect(component.error).toBe('AI job generation service is temporarily unavailable.');
    expect(component.isLoading).toBeFalse();
  });
});

